package benchmarking

import BenchCp
import bench.reproduceTests
import generateTestClass
import loadWebAppBenchCp
import logTime
import machine.JcConcreteMachineOptions
import machine.JcSpringAnalysisMode
import machine.JcSpringMachine
import machine.JcSpringMachineOptions
import machine.JcSpringTestObserver
import org.jacodb.api.jvm.ext.toType
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
import org.usvm.jvm.util.nonAbstractClasses
import org.usvm.logger
import org.usvm.machine.JcMachineOptions
import java.io.PrintStream
import java.nio.file.Path
import kotlin.io.path.div
import kotlin.system.exitProcess
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun main() {
    val benchmark = getBenchmark()
    val stdout = System.out
    println(benchmark.benchPath)
    val benchCp = logTime("Init jacodb") {
        val benchDir = benchmark.benchPath
        loadWebAppBenchCp(benchDir / "classes", benchDir / "lib")
    }
    logTime("Analysis ALL") {
        benchCp.use { analyzeBench(it, benchmark) }
    }
    System.setOut(stdout)
    exitProcess(0)
}

private fun analyzeBench(benchmark: BenchCp, benchDescription: BenchDescription) {
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

    val fileStream = PrintStream(benchDescription.logPath.toFile())
    System.setOut(fileStream)

    val options = UMachineOptions(
        useSoftConstraints = false,
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        coverageZone = CoverageZone.METHOD,
        exceptionsPropagation = false,
        stepLimit = 5000u,
        timeout = 1.minutes,
        solverType = SolverType.YICES,
        loopIterationLimit = 2,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )

    val jcMachineOptions = JcMachineOptions(
        forkOnImplicitExceptions = true,
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
}

private fun getBenchmark(): BenchDescription {
    return BenchDescription(
        Path.of(System.getProperty("usvm.benchmark")),
        Path.of(System.getProperty("usvm.log")),
        Path.of(System.getProperty("usvm.errors"))
    )
}
