package bench

import SpringTestRenderer
import SpringTestReproducer
import features.JcClinitFeature
import features.JcEncodingFeature
import features.JcGeneratedTypesFeature
import features.JcInitFeature
import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcRawAssignInst
import org.jacodb.api.jvm.cfg.JcRawClassConstant
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.packageName
import org.jacodb.api.jvm.ext.toType
import org.jacodb.approximation.Approximations
import org.jacodb.impl.JcRamErsSettings
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.jacodb
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldNode
import org.objectweb.asm.Type
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.jvm.util.isSameSignature
import org.usvm.jvm.util.replace
import org.usvm.jvm.util.write
import org.usvm.logger
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.interpreter.transformers.JcStringConcatTransformer
import util.database.JcTableInfoCollector
import org.usvm.util.classpathWithApproximations
import machine.JcConcreteMachineOptions
import machine.JcSpringMachine
import machine.JcSpringMachineOptions
import machine.JcSpringTestObserver
import machine.SpringAnalysisMode
import machine.interpreter.transformers.springjpa.JcDataclassTransformer
import machine.interpreter.transformers.springjpa.JcRepositoryCrudTransformer
import machine.interpreter.transformers.springjpa.JcRepositoryQueryTransformer
import machine.interpreter.transformers.springjpa.JcRepositoryTransformer
import org.jacodb.api.jvm.ext.jvmName
import org.jacodb.impl.cfg.JcInstListImpl
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.tree.ClassNode
import org.usvm.CoverageZone
import testGeneration.SpringTestInfo
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.walk
import kotlin.system.exitProcess
import kotlin.system.measureNanoTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.nanoseconds

private fun loadWebPetClinicBench(): BenchCp {
    val petClinicDir = Path("/Users/michael/Documents/Work/spring-petclinic/build/libs/BOOT-INF")
    return loadWebAppBenchCp(petClinicDir / "classes", petClinicDir / "lib")
}

private fun loadWebGoatBench(): BenchCp {
    val webGoatDir = Path("/Users/michael/Documents/Work/WebGoat/target/build/BOOT-INF")
    return loadWebAppBenchCp(webGoatDir / "classes", webGoatDir / "lib")
}

private fun loadKafdropBench(): BenchCp {
    val kafdropDir = Path("/Users/michael/Documents/Work/kafdrop/target/build/BOOT-INF")
    return loadWebAppBenchCp(kafdropDir / "classes", kafdropDir / "lib")
}

private fun loadKlawBench(): BenchCp {
    val klawDir = Path("/Users/michael/Documents/Work/klaw/core/target/build/BOOT-INF")
    return loadWebAppBenchCp(klawDir / "classes", klawDir / "lib")
}

private fun loadSynthBench(): BenchCp {
    val benchDir = Path("C:/Users/arthur/Documents/usvm-spring-benchmarks/build/libs/BOOT-INF")
    return loadWebAppBenchCp(benchDir / "classes", benchDir / "lib")
}

private fun loadJHipsterBench(): BenchCp {
    val benchDir = Path("/Users/michael/Documents/Work/jhipster-registry/target/build/BOOT-INF")
    return loadWebAppBenchCp(benchDir / "classes", benchDir / "lib")
}

fun main() {
    val benchCp = logTime("Init jacodb") {
        loadWebPetClinicBench()
    }

    logTime("Analysis ALL") {
        benchCp.use { analyzeBench(it) }
    }
}

private class BenchCp(
    val cp: JcClasspath,
    val db: JcDatabase,
    val classLocations: List<JcByteCodeLocation>,
    val depsLocations: List<JcByteCodeLocation>,
    val cpFiles: List<File>,
    val classes: List<File>,
    val dependencies: List<File>,
) : AutoCloseable {
    override fun close() {
        cp.close()
        db.close()
    }
}

private fun loadBench(
    db: JcDatabase,
    cpFiles: List<File>,
    classes: List<File>,
    dependencies: List<File>,
    isPureClasspath: Boolean = true,
    tablesInfo: JcTableInfoCollector? = null
    ) = runBlocking {
    val features = mutableListOf(
        UnknownClasses,
        JcStringConcatTransformer,
        JcClinitFeature,
        JcInitFeature,
        JcEncodingFeature,
        JcGeneratedTypesFeature
    )

    if (!isPureClasspath) {
        val dbFeatures = listOf(
            JcRepositoryCrudTransformer,
            JcRepositoryQueryTransformer,
            JcRepositoryTransformer,
            JcDataclassTransformer(tablesInfo!!)
        )
        features.addAll(dbFeatures)
    }

    val cp = db.classpathWithApproximations(cpFiles, features)

    val classLocations = cp.locations.filter { it.jarOrFolder in classes }
    val depsLocations = cp.locations.filter { it.jarOrFolder in dependencies }
    BenchCp(cp, db, classLocations, depsLocations, cpFiles, classes, dependencies)
}

private fun loadBenchCp(classes: List<File>, dependencies: List<File>): BenchCp = runBlocking {
    val springTestDeps =
        System.getenv("usvm.jvm.springTestDeps.paths")
            .split(";")
            .map { File(it) }

    val usvmConcreteApiJarPath = File(System.getenv("usvm.jvm.concrete.api.jar.path"))
    check(usvmConcreteApiJarPath.exists()) { "Concrete API jar does not exist" }

    var cpFiles = classes + dependencies + usvmConcreteApiJarPath
    // TODO: add springTestDeps only if user's dependencies do not contain them
    cpFiles += springTestDeps

    val db = jacodb {
        useProcessJavaRuntime()

        persistenceImpl(JcRamErsSettings)

        installFeatures(InMemoryHierarchy)
        installFeatures(Usages)
        installFeatures(Approximations)

        loadByteCode(cpFiles)

//        val persistenceLocation = classes.first().parentFile.resolve("jcdb.db")
//        persistent(persistenceLocation.absolutePath)
    }

    db.awaitBackgroundJobs()
    loadBench(db, cpFiles, classes, dependencies, true)
}

private fun loadWebAppBenchCp(classes: Path, dependencies: Path): BenchCp =
    loadWebAppBenchCp(listOf(classes), dependencies)

@OptIn(ExperimentalPathApi::class)
private fun loadWebAppBenchCp(classes: List<Path>, dependencies: Path): BenchCp =
    loadBenchCp(
        classes = classes.map { it.toFile() },
        dependencies = dependencies
            .walk(PathWalkOption.INCLUDE_DIRECTORIES)
            .filter { it.extension == "jar" }
            .map { it.toFile() }
            .toList()
    )

private val JcClassOrInterface.jvmDescriptor: String get() = name.jvmName()

fun allByAnnotation(allClasses: Sequence<JcClassOrInterface>, annotationName: String) =
    allClasses.filter { it.annotations.any { annotation -> annotation.name == annotationName } }

const val enableSecurity: Boolean = false

private fun addSecurityConfigs(testClassNode: ClassNode, nonAbstractClasses: Sequence<JcClassOrInterface>) {
    val importAnnotationName = "org.springframework.context.annotation.Import".jvmName()
    val securityConfigs = allByAnnotation(
        nonAbstractClasses,
        "org.springframework.security.config.annotation.web.configuration.EnableWebSecurity"
    )
    val importAnnotationNode = AnnotationNode(importAnnotationName)
    val securityConfigsAsm = securityConfigs.map { Type.getType(it.jvmDescriptor) }.toList()
    importAnnotationNode.values = listOf("value", securityConfigsAsm)
    testClassNode.visibleAnnotations.add(importAnnotationNode)
}

private fun disableSecurity(testClassNode: ClassNode) {
    /* Result:
        @WebMvcTest(
            excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = WebSecurityConfigurer.class)
            },
            excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class,
                OAuth2ClientAutoConfiguration.class,
                OAuth2ResourceServerAutoConfiguration.class
            }
        )
     */
    val webMvcTestClassName = "org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest".jvmName()
    val webMvcTestAnnotation =
        testClassNode.visibleAnnotations.find { it.desc == webMvcTestClassName }
            ?: error("WebMvcTest annotation not found for ${testClassNode.name}")
    val filterClassName = "org.springframework.context.annotation.ComponentScan\$Filter".jvmName()
    val filterAnnotation = AnnotationNode(filterClassName)
    val filterTypeName = "org.springframework.context.annotation.FilterType".jvmName()
    val assignableFilterType = arrayOf(filterTypeName, "ASSIGNABLE_TYPE")
    val configurerName = "org.springframework.security.config.annotation.web.WebSecurityConfigurer".jvmName()
    filterAnnotation.values = listOf(
        "type", assignableFilterType,
        "value", listOf(Type.getType(configurerName))
    )

    val excludedConfigurations = listOf(
        "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
        "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
    )
    webMvcTestAnnotation.values = listOf(
        "excludeFilters", listOf(filterAnnotation),
        "excludeAutoConfiguration", excludedConfigurations.map { Type.getType(it.jvmName()) }
    )
}

private fun generateTestClass(benchmark: BenchCp): BenchCp {
    val cp = benchmark.cp

    val springDirFile = File(System.getenv("springDir"))
    check(springDirFile.exists()) { "Generated directory ${springDirFile.absolutePath} does not exist" }

    val repositoryType = cp.findClass("org.springframework.data.repository.Repository")
    val nonAbstractClasses = cp.nonAbstractClasses(benchmark.classLocations)
    val repositories = runBlocking { cp.hierarchyExt() }
        .findSubClasses(repositoryType, entireHierarchy = true, includeOwn = false)
        .filter { benchmark.classLocations.contains(it.declaration.location.jcLocation) }
        .toList() + allByAnnotation(nonAbstractClasses, "org.springframework.stereotype.Repository")
    val services = allByAnnotation(nonAbstractClasses, "org.springframework.stereotype.Service")
    val mockBeans = repositories + services
    val testClass = cp.findClass("generated.org.springframework.boot.TestClass")

    val webApplicationPackage = allByAnnotation(nonAbstractClasses, "org.springframework.boot.autoconfigure.SpringBootApplication")
        .firstOrNull()?.packageName
        ?: throw IllegalArgumentException("No entry classes found (with SpringBootApplication annotation)")

    val entryPackagePath = webApplicationPackage.replace('.', '/')

    val testClassName = "StartSpringTestClass"
    val testClassFullName = "$entryPackagePath/$testClassName"

    testClass.withAsmNode { classNode ->
        classNode.name = testClassFullName
        val mockBeanAnnotationName = "org.springframework.boot.test.mock.mockito.MockBean".jvmName()
        mockBeans.forEach { mockBeanType ->
            val name = mockBeanType.simpleName.replaceFirstChar { it.lowercase(Locale.getDefault()) }
            val field = FieldNode(Opcodes.ACC_PRIVATE, name, mockBeanType.jvmDescriptor, null, null)
            field.visibleAnnotations = listOf(AnnotationNode(mockBeanAnnotationName))
            classNode.fields.add(field)
        }

        if (enableSecurity) {
            addSecurityConfigs(classNode, nonAbstractClasses)
        }

        classNode.write(cp, springDirFile.resolve("$testClassFullName.class").toPath(), checkClass = true)
    }

    System.setProperty("generatedTestClass", testClassFullName.replace('/', '.'))

    val tablesInfo = DatabaseGenerator(cp, springDirFile, repositories).generateJPADatabase()

    val startSpringClass = cp.findClassOrNull("generated.org.springframework.boot.StartSpring")!!
    startSpringClass.withAsmNode { startSpringAsmNode ->
        val startSpringMethod = startSpringClass.declaredMethods.find { it.name == "startSpring" }!!
        startSpringMethod.withAsmNode { startSpringMethodAsmNode ->
            val rawInstList = startSpringMethod.rawInstList.instructions.toMutableList()
            val assign = rawInstList[3] as JcRawAssignInst
            val classConstant = assign.rhv as JcRawClassConstant
            val newClassConstant = JcRawClassConstant(TypeNameImpl.fromTypeName(testClassFullName), classConstant.typeName)
            val newAssign = JcRawAssignInst(assign.owner, assign.lhv, newClassConstant)
            rawInstList[3] = newAssign
            val newNode = MethodNodeBuilder(startSpringMethod, JcInstListImpl(rawInstList)).build()
            val asmMethods = startSpringAsmNode.methods
            val asmMethod = asmMethods.find { startSpringMethodAsmNode.isSameSignature(it) }
            check(asmMethods.replace(asmMethod, newNode))
        }
        startSpringAsmNode.name = "NewStartSpring"
        startSpringAsmNode.write(cp, springDirFile.resolve("NewStartSpring.class").toPath(), checkClass = true)
    }
    runBlocking {
        benchmark.db.load(springDirFile)
        benchmark.db.awaitBackgroundJobs()
    }
    val newCpFiles = benchmark.cpFiles + springDirFile
    val newClasses = benchmark.classes + springDirFile
    return loadBench(benchmark.db, newCpFiles, newClasses, benchmark.dependencies, false, tablesInfo)
}

private fun analyzeBench(benchmark: BenchCp) {
    val newBench = generateTestClass(benchmark)
    val cp = newBench.cp
    val nonAbstractClasses = cp.nonAbstractClasses(newBench.classLocations)
    val startClass = nonAbstractClasses.find { it.simpleName == "NewStartSpring" }!!.toType()
    val method = startClass.declaredMethods.find { it.name == "startSpring" }!!
    // using file instead of console
    val fileStream = PrintStream("springLog.ansi")
    System.setOut(fileStream)
    val options = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        coverageZone = CoverageZone.METHOD,
        exceptionsPropagation = false,
        timeout = 3.minutes,
        solverType = SolverType.YICES,
        loopIterationLimit = 2,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )
    val jcMachineOptions = JcMachineOptions(
        forkOnImplicitExceptions = false,
        arrayMaxSize = 10_000,
    )
    val jcConcreteMachineOptions = JcConcreteMachineOptions(
        projectLocations = newBench.classLocations,
        dependenciesLocations = newBench.depsLocations,
    )
    val jcSpringMachineOptions = JcSpringMachineOptions(
        springAnalysisMode = SpringAnalysisMode.WebMVCTest
    )

    val testObserver = JcSpringTestObserver()

    val machine = JcSpringMachine(
        cp,
        options,
        jcMachineOptions,
        jcConcreteMachineOptions,
        jcSpringMachineOptions,
        testObserver
    )

    try {
        machine.analyze(method.method)
    } catch (e: Throwable) {
        logger.error(e) { "Machine failed" }
    }

    reproduceTests(testObserver.generatedTests, jcConcreteMachineOptions, cp)

    exitProcess(0)
}

private fun reproduceTests(
    tests: List<SpringTestInfo>,
    jcConcreteMachineOptions: JcConcreteMachineOptions,
    cp: JcClasspath
) {
    val testReproducer by lazy { SpringTestReproducer(jcConcreteMachineOptions, cp) }
    val testRenderer by lazy { SpringTestRenderer(cp) }
    val reproducingResults = mutableMapOf<JcMethod, Pair<String, Boolean>>()

    for (testInfo in tests) {
        val rendered = testRenderer.render(testInfo.test, testInfo.method, testInfo.isExceptional)
        val reproduced = testReproducer.reproduce(testInfo.test)
        reproducingResults[testInfo.method] = rendered to reproduced
    }

    testReproducer.kill()

    println("Tests count: ${tests.size}")
    val notReproduced = reproducingResults.filter { (_, value) -> !value.second }
    if (notReproduced.isEmpty()) {
        println("All reproduced")
        return
    }

    println("Reproduced ${tests.size - notReproduced.size} of ${tests.size} tests")
    var sb = StringBuilder()
    sb = sb.appendLine("Not reproduced tests:")
    for ((method, value) in notReproduced) {
        sb = sb.appendLine("$method:")
        sb = sb.appendLine(value.first)
    }

    println(sb.toString())
}

private fun JcClasspath.nonAbstractClasses(locations: List<JcByteCodeLocation>): Sequence<JcClassOrInterface> =
    locations
        .asSequence()
        .flatMap { it.classNames ?: emptySet() }
        .mapNotNull { findClassOrNull(it) }
        .filterNot { it is JcUnknownClass }
        .filterNot { it.isAbstract || it.isInterface || it.isAnonymous }
        .sortedBy { it.name }

private fun <T> logTime(message: String, body: () -> T): T {
    val result: T
    val time = measureNanoTime {
        result = body()
    }
    logger.info { "Time: $message | ${time.nanoseconds}" }
    return result
}
