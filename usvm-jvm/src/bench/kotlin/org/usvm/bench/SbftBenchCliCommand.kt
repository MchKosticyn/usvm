package org.usvm.bench

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mu.KLogging
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.approximation.Approximations
import org.jacodb.impl.JcRamErsSettings
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.jacodb
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorFairnessStrategy
import org.usvm.UMachineOptions
import org.usvm.bench.project.MethodId
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.interpreter.transformers.JcStringConcatTransformer
import org.usvm.util.classpathWithApproximations
import java.io.File
import kotlin.time.Duration.Companion.seconds

private val logger = object : KLogging() {}.logger

data class SbftBench(
    val name: String,
    val classNames: List<String>,
    val projectJars: List<File>,
    val depsJars: List<File>
)

class SbftBenchCliCommand : CliktCommand() {
    val machineTimeout by option("-t", help = "Timeout in seconds")
        .int().convert { it.seconds }.default(45.seconds)

    val parallelismLevel by option("-p", help = "Parallelism level (number of threads)")
        .int().default(1)

    val saveStatsToMongo by option("--mongo", help = "Save run stats to Mongo database")
        .boolean().default(false)

    val runId by option("-id", help = "Identifier of the benchmark run")
        .default(System.currentTimeMillis().toString())

    val className by option("-c", help = "FQN of specific class to run benchmark on")

    val methodId by option("-m", help = "Id of specific method to run benchmark on")
        .convert { MethodId.decodeFromString(it) }

    val checkpointClassName by option("--checkpoint", help = "If specified, bench will start from that class (only -p 1 supported)")

    private fun JcClasspath.allClasses(locations: List<JcByteCodeLocation>): Sequence<JcClassOrInterface> =
        locations
            .asSequence()
            .flatMap { it.classNames ?: emptySet() }
            .mapNotNull { findClassOrNull(it) }
            .filterNot { it is JcUnknownClass }
            .filterNot { it.isInterface || it.isAnonymous }
            .sortedBy { it.name }

    private suspend fun SbftBench.getClasspath(javaHome: String? = null): JcClasspath {
        val allJars = projectJars + depsJars

        val db = jacodb {
            persistenceImpl(JcRamErsSettings)

            if (javaHome != null) {
                useJavaRuntime(File(javaHome))
            } else {
                useProcessJavaRuntime()
            }

            installFeatures(InMemoryHierarchy)
            installFeatures(Usages)
            installFeatures(Approximations)

            loadByteCode(allJars)
        }

        db.awaitBackgroundJobs()

        val features = mutableListOf(UnknownClasses, JcStringConcatTransformer)
        return runBlocking {
            db.classpathWithApproximations(allJars, features)
        }
    }

    private fun getBenches(): List<SbftBench> {
        val classLoader = Thread.currentThread().contextClassLoader
        val sbftResourcesPath =
            File(classLoader.getResource("sbft")?.toURI()
                ?: throw IllegalStateException("Resources folder not found"))

        val sbftCasesPath = sbftResourcesPath.resolve("cases")
        val sbftJarsPath = sbftResourcesPath.resolve("jars")

        // Get the list of projects in sbft_cases
        return sbftCasesPath.listFiles()?.mapNotNull { projectDir ->
            if (projectDir.isDirectory) {
                val projectName = projectDir.name

                // Read class names from list
                val listFile = File(projectDir, "list")
                val classNames = if (listFile.exists()) {
                    listFile.readLines()
                } else {
                    emptyList()
                }

                // Get jar paths from sbft_projects
                val jarsDir = File(sbftJarsPath, projectName)

                val (projectJarPaths, depsJarPaths) = if (jarsDir.exists() && jarsDir.isDirectory) {
                    val pj = jarsDir.listFiles { _, name -> name.endsWith(".jar") && name.startsWith(projectDir.name) }?.toList() ?: emptyList()
                    val dj = jarsDir.listFiles { _, name -> name.endsWith(".jar") && !name.startsWith(projectDir.name) }?.toList() ?: emptyList()
                    pj to dj
                } else {
                    throw IllegalStateException("Cannot find SBFT jars")
                }

                check(projectJarPaths.isNotEmpty()) {
                    "Cannot find SBFT project jars"
                }

                // Create SbftBench object
                SbftBench(name = projectName, classNames = classNames, projectJars = projectJarPaths, depsJars = depsJarPaths)
            } else {
                null
            }
        } ?: emptyList()
    }

    override fun run() {
        require(parallelismLevel > 0) { "Parallelism level must be > 0" }

        logger.info { "Java Home: " + System.getProperty("java.home") }

        val statisticsReporter =
          if (saveStatsToMongo) BenchMongoReporter("usvm_sbft_bench") else BenchCmdReporter

        var checkpointReached = checkpointClassName == null

        val fixedMethodId = methodId

        for (sbftBench in getBenches()) {
            if (className != null && !sbftBench.classNames.contains(className)) {
                continue
            }

            if (!checkpointReached && checkpointClassName !in sbftBench.classNames) {
                logger.warn { "Skipping ${sbftBench.name}" }
                continue
            }

            if (fixedMethodId != null && fixedMethodId.className !in sbftBench.classNames) {
                continue
            }

            logger.info { "Running bench on ${sbftBench.name}..." }
            val cp = runBlocking { sbftBench.getClasspath() } // TODO: do we need specific Java Home?

            val options = UMachineOptions(
                timeout = machineTimeout,
                pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
                pathSelectorFairnessStrategy = PathSelectorFairnessStrategy.COMPLETELY_FAIR,
                stopOnCoverage = 100,
                coverageZone = CoverageZone.CLASS
            )

            val projectLocations = cp.locations.filter { it.jarOrFolder in sbftBench.projectJars }
            val depsLocations = cp.locations.filter { it.jarOrFolder in sbftBench.depsJars }

            if (depsLocations.isEmpty()) {
                logger.warn { "No dependency locations are detected for project ${sbftBench.name}! USVM won't detect methods to call concretely." }
            }

            val jcMachineOptions = JcMachineOptions(
                projectLocations = projectLocations,
                dependenciesLocations = depsLocations
            )

            val configId = statisticsReporter.reportConfig(
                System.currentTimeMillis(),
                options,
                jcMachineOptions,
                runId,
                -1,
                -1,
                sbftBench.name
            )

            val fixedClassName = className
            var classNamesToRun = if (fixedClassName != null) listOf(fixedClassName) else sbftBench.classNames
            classNamesToRun = if (fixedMethodId != null) listOf(fixedMethodId.className) else classNamesToRun

            if (parallelismLevel == 1 || classNamesToRun.size == 1) {
                for ((index, className) in classNamesToRun.withIndex()) {
                    if (className == checkpointClassName) {
                        checkpointReached = true
                    }
                    if (!checkpointReached) {
                        logger.info { "Skipping $className" }
                        continue
                    }
                    logger.info { "Analyzing class ${index + 1}/${classNamesToRun.size} $className" }
                    analyzeClass(className, cp, options, jcMachineOptions, statisticsReporter, configId)
                }
            } else {
                runBlocking {
                    val semaphore = Semaphore(parallelismLevel)
                    coroutineScope {
                        classNamesToRun.toList().mapIndexed { i, className ->
                            async(Dispatchers.Default) {
                                semaphore.withPermit {
                                    logger.info { "Analyzing class ${i + 1}/${classNamesToRun} $className" }
                                    analyzeClass(className, cp, options, jcMachineOptions, statisticsReporter, configId)
                                }
                            }
                        }.awaitAll()
                    }
                }
            }
        }
    }

    // Trying to follow
    // https://github.com/UnitTestBot/UTBotJava/blob/228072f78c12810311622e20d6e18e23a12a24ea/utbot-junit-contest/src/main/kotlin/org/utbot/contest/Contest.kt#L482
    private fun JcClassOrInterface.selectBenchMethods(visitedClasses: MutableSet<JcClassOrInterface> = mutableSetOf()): List<JcMethod> {
        visitedClasses.add(this)
        return declaredMethods.filterNot {
            it.isAbstract || it.isNative
        } + innerClasses.filter { it !in visitedClasses }.flatMap {
            it.selectBenchMethods(visitedClasses)
        }
    }

    private fun analyzeClass(jcClassName: String, cp: JcClasspath, options: UMachineOptions, jcMachineOptions: JcMachineOptions, statisticsReporter: BenchStatisticsReporter, configId: String) {
        val jcClass = cp.findClass(jcClassName)
        try {
            JcBenchMachine(
                cp,
                options,
                { s, e ->
                    statisticsReporter.reportInternalFailure(s.entrypoint, e, configId) },
                jcMachineOptions
            ).use { machine ->
                var methodsToExplore = jcClass.selectBenchMethods()
                val fixedMethodId = methodId
                if (fixedMethodId != null) {
                    methodsToExplore = methodsToExplore.filter { it.name == fixedMethodId.methodName }
                }
                val result = machine.analyze(methodsToExplore, emptyList())
                statisticsReporter.reportResult(
                    jcClass,
                    methodsToExplore,
                    result.states,
                    configId,
                    result.coverage,
                    result.elapsedTimeMillis,
                    result.stepsMade,
                    result.statesInPathSelector
                )
            }
        } catch (e: Throwable) {
            statisticsReporter.reportClassCriticalFail(jcClass, e, configId)
        }
    }
}

fun main(args: Array<String>) = SbftBenchCliCommand().main(args)
