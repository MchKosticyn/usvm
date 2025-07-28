package benchmarking

import BenchCp
import bench.analyzeBench
import loadWebAppBenchCp
import logTime
import org.usvm.CoverageZone
import org.usvm.PathSelectionStrategy
import org.usvm.SolverType
import org.usvm.UMachineOptions
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
        benchCp.use { runBenchmark(it, benchmark) }
    }
    System.setOut(stdout)
    exitProcess(0)
}

private fun runBenchmark(bench: BenchCp, benchDescription: BenchDescription) {
    // using file instead of console
    val fileStream = PrintStream(benchDescription.logPath.toFile())
    System.setOut(fileStream)

    val options = UMachineOptions(
        useSoftConstraints = false,
        pathSelectionStrategies = listOf(PathSelectionStrategy.BFS),
        coverageZone = CoverageZone.METHOD,
        exceptionsPropagation = false,
        stepLimit = 200000u,
        timeout = 5.minutes,
        solverType = SolverType.YICES,
        loopIterationLimit = 2,
        solverTimeout = Duration.INFINITE, // we do not need the timeout for a solver in tests
        typeOperationsTimeout = Duration.INFINITE, // we do not need the timeout for type operations in tests
    )

    analyzeBench(bench, options)
}

private fun getBenchmark(): BenchDescription {
    return BenchDescription(
        Path.of(System.getProperty("usvm.benchmark")),
        Path.of(System.getProperty("usvm.log")),
        Path.of(System.getProperty("usvm.errors"))
    )
}
