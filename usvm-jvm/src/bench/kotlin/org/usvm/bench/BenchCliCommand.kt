package org.usvm.bench

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.clikt.parameters.types.boolean
import com.github.ajalt.clikt.parameters.types.int
import com.github.ajalt.clikt.parameters.types.path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.awaitAll
import mu.KLogging
import org.jacodb.api.jvm.JcMethod
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.UMachineOptions
import org.usvm.bench.project.MethodId
import org.usvm.bench.project.Project
import org.usvm.bench.project.getMethod
import org.usvm.machine.JcMachineOptions
import kotlin.io.path.name
import kotlin.random.Random
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

private val logger = object : KLogging() {}.logger

class BenchCliCommand : CliktCommand() {

    val projectDir by argument(help = "Project directory")
        .path(mustExist = false, canBeFile = false, canBeDir = true)

    val machineTimeout by option("-t", help = "Timeout in seconds")
        .int().convert { it.seconds }.default(60.minutes)

    val parallelismLevel by option("-p", help = "Parallelism level (number of threads)")
        .int().default(1)

    val methodId by option("-m", help = "If specified, single method id to execute")

    val saveStatsToMongo by option("--mongo", help = "Save run stats to Mongo database")
        .boolean().default(false)

    val comment by option("-c", help = "Identifier of the benchmark run")
        .default(System.currentTimeMillis().toString())

    private val randomSeed = 37
    private val randomMethodsToTake = 180

    private val random = Random(randomSeed)

    override fun run() {
        require(parallelismLevel > 0) { "Parallelism level must be > 0" }

        logger.info { "Java Home: " + System.getProperty("java.home") }

        val statisticsReporter =
            if (saveStatsToMongo) BenchMongoReporter("usvm_bench") else BenchCmdReporter

        runBlocking { Project.fromDir(projectDir) }.use { project ->
            val options = UMachineOptions(
                timeout = machineTimeout,
                pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
                stopOnCoverage = 100,
                coverageZone = CoverageZone.METHOD
            )

            val projectLocations = project.cp.locations.filter { it.jarOrFolder in project.projectFiles }
            val dependenciesLocations = project.cp.locations.filter { it.jarOrFolder in project.dependencyFiles }

            val jcMachineOptions = JcMachineOptions(
                projectLocations = projectLocations,
                dependenciesLocations = dependenciesLocations
            )

            val projectName = projectDir.name.split("_").last()

            val configId = statisticsReporter.reportConfig(
                System.currentTimeMillis(),
                options,
                jcMachineOptions,
                comment,
                randomSeed,
                randomMethodsToTake,
                projectName
            )

            val fixedMethodId = methodId
            val methods = if (fixedMethodId == null) {
                project.discoverMethods().toList()
            } else {
                requireNotNull(project.cpWithApproximations.getMethod(MethodId.decodeFromString(fixedMethodId))) {
                    "Cannot resolve method with id $fixedMethodId"
                }.let(::listOf)
            }
            if (parallelismLevel == 1 || methods.size == 1) {
                methods.forEachIndexed { i, jcMethod ->
                    logger.info { "Analyzing method ${i + 1}/${methods.size} ${jcMethod.enclosingClass.name}.${jcMethod.name}" }
                    analyzeMethod(jcMethod, project, options, jcMachineOptions, statisticsReporter, configId)
                }
            } else {
                runBlocking {
                    val semaphore = Semaphore(parallelismLevel)
                    coroutineScope {
                        methods.toList().mapIndexed { i, jcMethod ->
                            async(Dispatchers.Default) {
                                semaphore.withPermit {
                                    logger.info { "Analyzing method ${i + 1}/${methods.size} ${jcMethod.enclosingClass.name}.${jcMethod.name}" }
                                    analyzeMethod(jcMethod, project, options, jcMachineOptions, statisticsReporter, configId)
                                }
                            }
                        }.awaitAll()
                    }
                }
            }
        }
    }

    private fun Project.discoverMethods(): List<JcMethod> {
       return cpWithApproximations.locations.filter { it.jarOrFolder in projectFiles }
            .flatMap { it.classNames ?: emptyList() }
           .mapNotNull { cpWithApproximations.findClassOrNull(it) }
           .filterNot { it is JcUnknownClass }
           .flatMap { it.declaredMethods }
           .filter { it.rawInstList.size != 0 && !it.isClassInitializer && it.parameters.isNotEmpty() }
           .shuffled(random)
           .take(randomMethodsToTake)
    }

    private fun analyzeMethod(jcMethod: JcMethod, project: Project, options: UMachineOptions, jcMachineOptions: JcMachineOptions, statisticsReporter: BenchStatisticsReporter, configId: String) {
        JcBenchMachine(
            project.cpWithApproximations,
            options,
            { _, e -> statisticsReporter.reportInternalFailure(jcMethod, e, configId) },
            jcMachineOptions
        ).use { machine ->
            val result = machine.analyze(jcMethod, emptyList())
            statisticsReporter.reportResult(
                jcMethod,
                result.states,
                configId,
                result.coverage,
                result.elapsedTimeMillis,
                result.stepsMade,
                result.statesInPathSelector
            )
        }
    }
}

fun main(args: Array<String>) = BenchCliCommand().main(args)
