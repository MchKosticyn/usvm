package bench

import SpringTestRenderer
import SpringTestReproducer
import features.JcClinitFeature
import features.JcEncodingFeature
import features.JcGeneratedTypesFeature
import features.JcInitFeature
import kotlinx.coroutines.runBlocking
import machine.JcConcreteMachineOptions
import machine.JcSpringAnalysisMode
import machine.JcSpringMachine
import machine.JcSpringMachineOptions
import machine.JcSpringTestObserver
import machine.interpreter.transformers.springjpa.JcDataclassTransformer
import machine.interpreter.transformers.springjpa.JcTableIdClassTransformer
import machine.interpreter.transformers.springjpa.JcRepositoryCrudTransformer
import machine.interpreter.transformers.springjpa.JcRepositoryQueryTransformer
import machine.interpreter.transformers.springjpa.JcRepositoryTransformer
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.cfg.JcRawClassConstant
import org.jacodb.api.jvm.cfg.JcRawReturnInst
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.jvmName
import org.jacodb.api.jvm.ext.packageName
import org.jacodb.api.jvm.ext.toType
import org.jacodb.approximation.Approximations
import org.jacodb.impl.JcRamErsSettings
import org.jacodb.impl.cfg.JcInstListImpl
import org.jacodb.impl.cfg.MethodNodeBuilder
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.features.hierarchyExt
import org.jacodb.impl.jacodb
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.Type
import org.objectweb.asm.tree.AnnotationNode
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.FieldInsnNode
import org.objectweb.asm.tree.LdcInsnNode
import org.objectweb.asm.tree.MethodInsnNode
import org.objectweb.asm.tree.MultiANewArrayInsnNode
import org.objectweb.asm.tree.TypeInsnNode
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.jvm.rendering.spring.webMvcTestRenderer.JcSpringMvcTestInfo
import org.usvm.jvm.rendering.testRenderer.JcTestInfo
import org.usvm.jvm.util.isSameSignature
import org.usvm.jvm.util.nonAbstractClasses
import org.usvm.jvm.util.replace
import org.usvm.jvm.util.write
import org.usvm.logger
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.interpreter.transformers.JcStringConcatTransformer
import org.usvm.test.api.UTest
import testGeneration.SpringTestInfo
import util.classpathWithSpringApproximations
import util.database.JcTableInfoCollector
import java.io.File
import java.io.PrintStream
import java.nio.file.Path
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

private fun loadBenchFromEnv(): BenchCp {
    val benchDir = Path(System.getenv("usvm.benchmark"))
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
    val dependencies: List<File>
) : AutoCloseable {
    override fun close() {
        cp.close()
        db.close()
    }

    fun bindMachineOptions(options: JcConcreteMachineOptions) {
        (cp.features?.find { it is JcRepositoryTransformer } as? JcRepositoryTransformer)?.bindMachineOptions(options)
    }
}

private fun loadBench(
    db: JcDatabase,
    cpFiles: List<File>,
    classes: List<File>,
    dependencies: List<File>,
    isPureClasspath: Boolean = true,
    tablesInfo: JcTableInfoCollector? = null,
    isNeedTrackTable: Boolean = false
) = runBlocking {
    val features = mutableListOf(
        // TODO: try to delete
        UnknownClasses,
        JcStringConcatTransformer,
        JcClinitFeature,
        JcInitFeature,
        JcEncodingFeature,
        JcGeneratedTypesFeature
    )

    if (!isPureClasspath) {
        val dbFeatures = listOf(
            JcRepositoryCrudTransformer(tablesInfo!!),
            JcRepositoryQueryTransformer,
            JcRepositoryTransformer,
            JcDataclassTransformer(tablesInfo, isNeedTrackTable),
            JcTableIdClassTransformer(tablesInfo)
        )
        features.addAll(dbFeatures)
    }

    val cp = db.classpathWithSpringApproximations(cpFiles, features)

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

private fun allByAnnotation(allClasses: Sequence<JcClassOrInterface>, annotationName: String) =
    allClasses.filter { it.annotations.any { annotation -> annotation.name == annotationName } }

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

private fun replaceTypeInClassNode(
    classNode: ClassNode,
    oldClassName: String,
    newClassName: String
) {
    check(!oldClassName.contains('/'))
    check(!newClassName.contains('/'))

    val oldClassSlashName = oldClassName.replace(".", "/")
    val oldClassJvmName = "L$oldClassSlashName;"
    val newClassSlashName = newClassName.replace(".", "/")
    val newClassJvmName = "L$newClassSlashName;"

    // Transform all field descriptors and signatures
    classNode.fields?.forEach { field ->
        field.desc = field.desc.replace(oldClassJvmName, newClassJvmName)
        field.signature = field.signature?.replace(oldClassJvmName, newClassJvmName)
    }

    // Transform all methods
    classNode.methods?.forEach { method ->
        // Update method descriptor and signature
        method.desc = method.desc.replace(oldClassJvmName, newClassJvmName)
        method.signature = method.signature?.replace(oldClassJvmName, newClassJvmName)

        // Process all instructions in the method
        for (inst in method.instructions) {
            when (inst) {
                is TypeInsnNode -> {
                    // NEW, ANEWARRAY, CHECKCAST, INSTANCEOF
                    if (inst.desc == oldClassSlashName) {
                        inst.desc = newClassSlashName
                    }
                }
                is FieldInsnNode -> {
                    // GETFIELD, PUTFIELD, GETSTATIC, PUTSTATIC
                    if (inst.owner == oldClassSlashName) {
                        inst.owner = newClassSlashName
                    }
                    inst.desc = inst.desc.replace(oldClassJvmName, newClassJvmName)
                }
                is MethodInsnNode -> {
                    // INVOKEVIRTUAL, INVOKESPECIAL, INVOKESTATIC, INVOKEINTERFACE
                    if (inst.owner == oldClassSlashName) {
                        inst.owner = newClassSlashName
                    }
                    inst.desc = inst.desc.replace(oldClassJvmName, newClassJvmName)
                }
                is LdcInsnNode -> {
                    // LDC Class constants
                    if (inst.cst is Type) {
                        val type = inst.cst as Type
                        if (type.className == oldClassName) {
                            inst.cst = Type.getType(newClassJvmName)
                        }
                    }
                }
                is MultiANewArrayInsnNode -> {
                    // MULTIANEWARRAY
                    inst.desc = inst.desc.replace(oldClassJvmName, newClassJvmName)
                }
            }
        }

        // Transform local variable types
        method.localVariables?.forEach { localVar ->
            localVar.desc = localVar.desc.replace(oldClassJvmName, newClassJvmName)
            localVar.signature = localVar.signature?.replace(oldClassJvmName, newClassJvmName)
        }

        // Transform exception types in try-catch blocks
        method.tryCatchBlocks?.forEach { tryCatch ->
            if (tryCatch.type == oldClassSlashName) {
                tryCatch.type = newClassSlashName
            }
        }
    }

    // Transform class signature (for generics)
    classNode.signature = classNode.signature?.replace(oldClassJvmName, newClassJvmName)
}

@Suppress("SameParameterValue")
private fun generateTestClass(benchmark: BenchCp, springAnalysisMode: JcSpringAnalysisMode): BenchCp {
    val cp = benchmark.cp

    val springDirFile = File(System.getenv("springDir"))
    check(springDirFile.exists()) { "Generated directory ${springDirFile.absolutePath} does not exist" }
    val classLocations = benchmark.classLocations
    val nonAbstractClasses = cp.nonAbstractClasses(classLocations)

    val repositoryType = cp.findClass("org.springframework.data.repository.Repository")
    val repositories = runBlocking { cp.hierarchyExt() }
        .findSubClasses(repositoryType, entireHierarchy = true, includeOwn = false)
        .filter { classLocations.contains(it.declaration.location.jcLocation) }
        .toList() + allByAnnotation(nonAbstractClasses, "org.springframework.stereotype.Repository")
    val entityManagerType = cp.findClassOrNull("jakarta.persistence.EntityManager")
    val hasJpa = repositories.isNotEmpty() || entityManagerType != null && entityManagerType !is JcUnknownClass

    val testClassTemplateName =
        if (hasJpa) "generated.org.springframework.boot.testClasses.SpringBootJpaTestClass"
        else "generated.org.springframework.boot.testClasses.SpringBootTestClass"

    val applicationClass = allByAnnotation(
        nonAbstractClasses,
        "org.springframework.boot.autoconfigure.SpringBootApplication"
    ).singleOrNull() ?: error("No entry classes found (with SpringBootApplication annotation)")
    val entryPackagePath = applicationClass.packageName.replace('.', '/')
    val testClassName = "NewSpringBootTestClass"
    val newTestClassSlashName = "$entryPackagePath/$testClassName"
    val newTestClassName = newTestClassSlashName.replace('/', '.')

    val testClassTemplate = cp.findClass(testClassTemplateName)
    testClassTemplate.withAsmNode { classNode ->
        classNode.name = newTestClassSlashName

        when (springAnalysisMode) {
            JcSpringAnalysisMode.SpringBootTest -> {
                val sprintBootTestAnnotation = classNode.visibleAnnotations.find {
                    it.desc == "org.springframework.boot.test.context.SpringBootTest".jvmName()
                } ?: error("SpringBootTest annotation not found")
                sprintBootTestAnnotation.values = listOf(
                    "classes", listOf(Type.getType(applicationClass.jvmDescriptor))
                )
            }
            JcSpringAnalysisMode.SpringJpaTest -> TODO("not supported yet")
        }

        replaceTypeInClassNode(classNode, testClassTemplateName, newTestClassName)
        classNode.write(cp, springDirFile.resolve("$newTestClassSlashName.class").toPath(), checkClass = true)
    }

    System.setProperty("generatedTestClass", newTestClassName)

    val tablesInfo = DatabaseGenerator(cp, springDirFile, repositories).generateJPADatabase()
    val isNeedTrackTable = springAnalysisMode == JcSpringAnalysisMode.SpringBootTest

    val startSpringTemplateName = "generated.org.springframework.boot.StartSpring"
    val newStartSpringName = "NewStartSpring"
    val startSpringClass = cp.findClassOrNull(startSpringTemplateName)!!
    startSpringClass.withAsmNode { startSpringAsmNode ->
        val chooseTestClassMethod = startSpringClass.declaredMethods.find { it.name == "chooseTestClass" }!!
        chooseTestClassMethod.withAsmNode { chooseTestClassMethodAsmNode ->
            val classConstant = JcRawClassConstant(
                TypeNameImpl.fromTypeName(newTestClassName),
                TypeNameImpl.fromTypeName("java.lang.Class")
            )
            val returnStmt = JcRawReturnInst(chooseTestClassMethod, classConstant)
            val newNode = MethodNodeBuilder(
                chooseTestClassMethod,
                JcInstListImpl(listOf(returnStmt))
            ).build()
            val asmMethods = startSpringAsmNode.methods
            val asmMethod = asmMethods.find { chooseTestClassMethodAsmNode.isSameSignature(it) }
            check(asmMethods.replace(asmMethod, newNode))
        }
        startSpringAsmNode.name = newStartSpringName
        replaceTypeInClassNode(startSpringAsmNode, startSpringTemplateName, newStartSpringName)
        startSpringAsmNode.write(cp, springDirFile.resolve("$newStartSpringName.class").toPath(), checkClass = true)
    }
    runBlocking {
        benchmark.db.load(springDirFile)
        benchmark.db.awaitBackgroundJobs()
    }
    val newCpFiles = benchmark.cpFiles + springDirFile
    val newClasses = benchmark.classes + springDirFile
    return loadBench(
        benchmark.db,
        newCpFiles,
        newClasses,
        benchmark.dependencies,
        false,
        tablesInfo,
        isNeedTrackTable
    )
}

private fun analyzeBench(benchmark: BenchCp) {
    val springAnalysisMode = JcSpringAnalysisMode.SpringBootTest
    val newBench = generateTestClass(benchmark, springAnalysisMode)

    val jcConcreteMachineOptions = JcConcreteMachineOptions(
        projectLocations = newBench.classLocations,
        dependenciesLocations = newBench.depsLocations,
    )
    newBench.bindMachineOptions(jcConcreteMachineOptions)
    val jcSpringMachineOptions = JcSpringMachineOptions(
        springAnalysisMode = springAnalysisMode
    )

    val cp = newBench.cp
    val nonAbstractClasses = cp.nonAbstractClasses(newBench.classLocations)
    val startClass = nonAbstractClasses.find { it.simpleName == "NewStartSpring" }!!.toType()
    val method = startClass.declaredMethods.find { it.name == "startSpring" }!!
    // using file instead of console
    val fileStream = PrintStream(System.getenv("usvm.log") ?: "springLog.ansi")
    System.setOut(fileStream)
    val options = UMachineOptions(
        useSoftConstraints = false,
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        coverageZone = CoverageZone.METHOD,
        exceptionsPropagation = false,
        timeout = 2.minutes,
        solverType = SolverType.YICES,
        loopIterationLimit = 2,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )
    val jcMachineOptions = JcMachineOptions(
        forkOnImplicitExceptions = false,
        arrayMaxSize = 10_000,
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

private fun SpringTestInfo.toRenderInfo(): Pair<UTest, JcSpringMvcTestInfo> {
    return this.test to JcSpringMvcTestInfo(this.method, this.isExceptional)
}

private fun createOrClear(file: File) {
    if (file.exists()) {
        file.listFiles()?.forEach { it.deleteRecursively() }
    } else {
        file.mkdirs()
    }
}

private fun renderTests(testRenderer: SpringTestRenderer, tests: List<Pair<UTest, JcTestInfo>>, dir: File) {
    val rendered = testRenderer.render(tests)
    for ((testClassInfo, result) in rendered) {
        val testFile = dir.resolve("${testClassInfo.testClassName}.java")
        testFile.writeText(result)
    }
}

private fun reproduceTests(
    tests: List<SpringTestInfo>,
    jcConcreteMachineOptions: JcConcreteMachineOptions,
    cp: JcClasspath
) {
    val testReproducer by lazy { SpringTestReproducer(jcConcreteMachineOptions, cp) }
    val testRenderer by lazy { SpringTestRenderer(cp) }

    val reproducedTests = mutableListOf<Pair<UTest, JcSpringMvcTestInfo>>()
    val notReproducedTests = mutableListOf<Pair<UTest, JcSpringMvcTestInfo>>()
    for (testInfo in tests) {
        val reproduced = testReproducer.reproduce(testInfo.test)
        if (reproduced)
            reproducedTests.add(testInfo.toRenderInfo())
        else notReproducedTests.add(testInfo.toRenderInfo())
    }
    testReproducer.kill()

    val currentDir = File(System.getProperty("user.dir"))
    val generatedTestsDir = currentDir.resolve("generatedTests")
    createOrClear(generatedTestsDir)
    val reproducedDir = generatedTestsDir.resolve("reproduced")
    createOrClear(reproducedDir)
    val notReproducedDir = generatedTestsDir.resolve("notReproduced")
    createOrClear(notReproducedDir)

    renderTests(testRenderer, reproducedTests, reproducedDir)
    renderTests(testRenderer, notReproducedTests, notReproducedDir)

    println("Reproduced ${reproducedTests.size} of ${tests.size} tests")
}

private fun <T> logTime(message: String, body: () -> T): T {
    val result: T
    val time = measureNanoTime {
        result = body()
    }
    logger.info { "Time: $message | ${time.nanoseconds}" }
    return result
}
