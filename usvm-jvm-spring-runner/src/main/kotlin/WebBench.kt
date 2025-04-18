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
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.jvm.util.isSameSignature
import org.usvm.jvm.util.replace
import org.usvm.jvm.util.write
import org.usvm.logger
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.interpreter.transformers.JcStringConcatTransformer
import org.usvm.util.classpathWithApproximations
import machine.JcConcreteMachineOptions
import machine.JcSpringMachine
import machine.JcSpringMachineOptions
import machine.JcSpringTestObserver
import machine.SpringAnalysisMode
import org.jacodb.impl.cfg.JcInstListImpl
import org.jacodb.impl.types.TypeNameImpl
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
    return loadWebAppBenchCp(petClinicDir / "classes", petClinicDir / "lib").apply {
        entrypointFilter = { it.enclosingClass.simpleName.startsWith("PetClinicApplication") }
    }
}

private fun loadWebGoatBench(): BenchCp {
    val webGoatDir = Path("/Users/michael/Documents/Work/WebGoat/target/build/BOOT-INF")
    return loadWebAppBenchCp(webGoatDir / "classes", webGoatDir / "lib").apply {
        entrypointFilter = { it.enclosingClass.simpleName.startsWith("WebGoatApplication") }
    }
}

private fun loadKafdropBench(): BenchCp {
    val kafdropDir = Path("/Users/michael/Documents/Work/kafdrop/target/build/BOOT-INF")
    return loadWebAppBenchCp(kafdropDir / "classes", kafdropDir / "lib").apply {
        entrypointFilter = { it.enclosingClass.simpleName.startsWith("WebGoatApplication") }
    }
}

private fun loadKlawBench(): BenchCp {
    val klawDir = Path("/Users/michael/Documents/Work/klaw/core/target/build/BOOT-INF")
    return loadWebAppBenchCp(klawDir / "classes", klawDir / "lib").apply {
        entrypointFilter = { it.enclosingClass.simpleName.startsWith("WebGoatApplication") }
    }
}

private fun loadSynthBench(): BenchCp {
    val benchDir = Path("C:/Users/arthur/Documents/usvm-spring-benchmarks/build/libs/BOOT-INF")
    return loadWebAppBenchCp(benchDir / "classes", benchDir / "lib").apply {
        entrypointFilter = { it.enclosingClass.simpleName.startsWith("SpringBenchmarks") }
    }
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
    var entrypointFilter: (JcMethod) -> Boolean = { true },
) : AutoCloseable {
    override fun close() {
        cp.close()
        db.close()
    }
}

private fun loadBench(db: JcDatabase, cpFiles: List<File>, classes: List<File>, dependencies: List<File>) = runBlocking {
    val features = listOf(UnknownClasses, JcStringConcatTransformer, JcClinitFeature, JcInitFeature, JcEncodingFeature, JcGeneratedTypesFeature)
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

    val cpFiles = classes + dependencies + springTestDeps + usvmConcreteApiJarPath

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
    loadBench(db, cpFiles, classes, dependencies)
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

private val JcClassOrInterface.jvmDescriptor: String get() = "L${name.replace('.','/')};"

fun allByAnnotation(allClasses: Sequence<JcClassOrInterface>, annotationName: String) =
    allClasses.filter { it.annotations.any { annotation -> annotation.name == annotationName } }

private fun generateTestClass(benchmark: BenchCp): BenchCp {
    val cp = benchmark.cp

    val springDirFile = File(System.getenv("springDir"))
    check(springDirFile.exists()) { "Generated directory ${springDirFile.absolutePath} does not exist" }

    val repositoryType = cp.findClass("org.springframework.data.repository.Repository")
//    val importAnnotation = cp.findClass("org.springframework.context.annotation.Import")
    val mockAnnotation = cp.findClass("org.springframework.boot.test.mock.mockito.MockBean")
    val nonAbstractClasses = cp.nonAbstractClasses(benchmark.classLocations)
//    val securityConfigs = allByAnnotation(
//        nonAbstractClasses,
//        "org.springframework.security.config.annotation.web.configuration.EnableWebSecurity"
//    )
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
        mockBeans.forEach { mockBeanType ->
            val name = mockBeanType.simpleName.replaceFirstChar { it.lowercase(Locale.getDefault()) }
            val field = FieldNode(Opcodes.ACC_PRIVATE, name, mockBeanType.jvmDescriptor, null, null)
            field.visibleAnnotations = listOf(AnnotationNode(mockAnnotation.jvmDescriptor))
            classNode.fields.add(field)
        }

//        val importAnnotationNode = AnnotationNode(importAnnotation.jvmDescriptor)
//        importAnnotationNode.values = listOf("value", securityConfigs.map { Type.getType(it.jvmDescriptor) }.toList())
//        classNode.visibleAnnotations.add(importAnnotationNode)
        classNode.write(cp, springDirFile.resolve("$testClassFullName.class").toPath(), checkClass = true)
    }

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
    return loadBench(benchmark.db, newCpFiles, newClasses, benchmark.dependencies)
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
        timeout = 1.minutes,
        solverType = SolverType.YICES,
        // TODO: hack #PS
//        loopIterationLimit = 2,
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
    val testReproducer by lazy { SpringTestReproducer(jcConcreteMachineOptions, cp, 1) }
    val testRenderer by lazy { SpringTestRenderer(cp) }
    val reproducingResults = mutableMapOf<JcMethod, Pair<String, Boolean>>()

    for (testInfo in tests) {
        val rendered = testRenderer.render(testInfo.test, testInfo.method, testInfo.isExceptional)
        val reproduced = testReproducer.reproduce(testInfo.test)
        reproducingResults[testInfo.method] = rendered to reproduced
    }

    testReproducer.kill()

    val notReproduced = reproducingResults.filter { (_, value) -> !value.second }
    check(notReproduced.isEmpty()) {
        var sb = StringBuilder()
        sb = sb.appendLine("Not reproduced tests:")
        for ((method, value) in notReproduced) {
            sb = sb.appendLine("$method:")
            sb = sb.appendLine(value.first)
        }

        sb.toString()
    }
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
