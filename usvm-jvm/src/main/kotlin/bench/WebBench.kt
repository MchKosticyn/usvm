package bench

import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcRawAssignInst
import org.jacodb.api.jvm.cfg.JcRawClassConstant
import org.jacodb.api.jvm.ext.findClass
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
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.FieldNode
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.api.util.JcConcreteMemoryClassLoader
import org.usvm.api.util.JcTestInterpreter
import org.usvm.logger
import org.usvm.machine.JcMachine
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.interpreter.transformers.JcStringConcatTransformer
import org.usvm.util.classpathWithApproximations
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
import java.util.Locale
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.Path
import kotlin.io.path.PathWalkOption
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.path.extension
import kotlin.io.path.walk
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

fun main() {
    val benchCp = logTime("Init jacodb") {
        loadKlawBench()
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
    val features = listOf(UnknownClasses, JcStringConcatTransformer)
    val cp = db.classpathWithApproximations(cpFiles, features)

    val classLocations = cp.locations.filter { it.jarOrFolder in classes }
    val depsLocations = cp.locations.filter { it.jarOrFolder in dependencies }
    BenchCp(cp, db, classLocations, depsLocations, cpFiles, classes, dependencies)
}

private fun loadBenchCp(classes: List<File>, dependencies: List<File>): BenchCp = runBlocking {
    val springApproximationDeps =
        System.getProperty("usvm.jvm.springApproximationsDeps.paths")
            .split(";")
            .map { File(it) }

    val cpFiles = classes + dependencies + springApproximationDeps

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

private fun generateTestClass(benchmark: BenchCp): BenchCp {
    val dir = Path(System.getProperty("generatedDir"))
    dir.createDirectories()
    val cp = benchmark.cp
    val repositoryType = cp.findClass("org.springframework.data.repository.Repository")
    val mockAnnotation = cp.findClass("org.springframework.boot.test.mock.mockito.MockBean")
    val repositories = runBlocking { cp.hierarchyExt() }
        .findSubClasses(repositoryType, entireHierarchy = true, includeOwn = false)
        .filter { benchmark.classLocations.contains(it.declaration.location.jcLocation) }
        .toList()
    val services =
        cp.nonAbstractClasses(benchmark.classLocations)
            .filter {
                it.annotations.any { annotation ->
                    annotation.name == "org.springframework.stereotype.Service"
                }
            }.toList()
    val mockBeans = repositories + services
    val testClass = cp.findClass("generated.org.springframework.boot.TestClass")
    val testClassName = "StartSpringTestClass"
    testClass.withAsmNode { classNode ->
//        classNode.visibleAnnotations = listOf()
        classNode.name = testClassName
        mockBeans.forEach { mockBeanType ->
            val name = mockBeanType.simpleName.replaceFirstChar { it.lowercase(Locale.getDefault()) }
            val field = FieldNode(Opcodes.ACC_PUBLIC, name, mockBeanType.jvmDescriptor, null, null)
            field.visibleAnnotations = listOf(AnnotationNode(mockAnnotation.jvmDescriptor))
            classNode.fields.add(field)
        }

        classNode.write(cp, dir.resolve("$testClassName.class"), checkClass = true)
    }

    val startSpringClass = cp.findClassOrNull("generated.org.springframework.boot.StartSpring")!!
    startSpringClass.withAsmNode { startSpringAsmNode ->
        val startSpringMethod = startSpringClass.declaredMethods.find { it.name == "startSpring" }!!
        startSpringMethod.withAsmNode { startSpringMethodAsmNode ->
            val rawInstList = startSpringMethod.rawInstList.toMutableList()
            val assign = rawInstList[3] as JcRawAssignInst
            val classConstant = assign.rhv as JcRawClassConstant
            val newClassConstant = JcRawClassConstant(TypeNameImpl(testClassName), classConstant.typeName)
            val newAssign = JcRawAssignInst(assign.owner, assign.lhv, newClassConstant)
            rawInstList.remove(rawInstList[3])
            rawInstList.insertAfter(rawInstList[2], newAssign)
            val newNode = MethodNodeBuilder(startSpringMethod, rawInstList).build()
            val asmMethods = startSpringAsmNode.methods
            val asmMethod = asmMethods.find { startSpringMethodAsmNode.isSameSignature(it) }
            check(asmMethods.replace(asmMethod, newNode))
        }
        startSpringAsmNode.name = "NewStartSpring"
        startSpringAsmNode.write(cp, dir.resolve("NewStartSpring.class"), checkClass = true)
    }
    val dirFile = dir.toFile()
    val lambdasDirFile = Path(System.getProperty("lambdasDir")).toFile()
    runBlocking {
        benchmark.db.load(dirFile)
        benchmark.db.load(lambdasDirFile)
        benchmark.db.awaitBackgroundJobs()
    }
    val newCpFiles = benchmark.cpFiles + dirFile + lambdasDirFile
    val newClasses = benchmark.classes + dirFile + lambdasDirFile
    return loadBench(benchmark.db, newCpFiles, newClasses, benchmark.dependencies)
}

private fun analyzeBench(benchmark: BenchCp) {
    val newBench = generateTestClass(benchmark)
    val cp = newBench.cp
    val nonAbstractClasses = cp.nonAbstractClasses(newBench.classLocations)
    val webApplicationClass =
        nonAbstractClasses.find {
            it.annotations.any { annotation ->
                annotation.name == "org.springframework.boot.autoconfigure.SpringBootApplication"
            }
        }
    JcConcreteMemoryClassLoader.webApplicationClass = webApplicationClass!!
    val startClass = nonAbstractClasses.find { it.simpleName == "NewStartSpring" }!!.toType()
    val method = startClass.declaredMethods.find { it.name == "startSpring" }!!
    // using file instead of console
    val fileStream = PrintStream("/Users/michael/Documents/Work/usvm/springLog.ansi")
    System.setOut(fileStream)
    val options = UMachineOptions(
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        coverageZone = CoverageZone.SPRING_APPLICATION,
        exceptionsPropagation = false,
        timeout = 1.minutes,
        solverType = SolverType.YICES,
        loopIterationLimit = 2,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )
    val jcMachineOptions =
        JcMachineOptions(
            projectLocations = newBench.classLocations,
            dependenciesLocations = newBench.depsLocations,
//            forkOnImplicitExceptions = false,
            arrayMaxSize = 10_000
        )
    val testResolver = JcTestInterpreter()
    JcMachine(cp, options, jcMachineOptions).use { machine ->
        val states = machine.analyze(method.method)
        states.map { testResolver.resolve(method, it) }
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

private fun JcClassOrInterface.publicAndProtectedMethods(): Sequence<JcMethod> =
    declaredMethods.asSequence()
        .filter { it.instList.size > 0 }
        .filter { it.isPublic || it.isProtected }
        .sortedBy { it.name }

private fun <T> logTime(message: String, body: () -> T): T {
    val result: T
    val time = measureNanoTime {
        result = body()
    }
    logger.info { "Time: $message | ${time.nanoseconds}" }
    return result
}
