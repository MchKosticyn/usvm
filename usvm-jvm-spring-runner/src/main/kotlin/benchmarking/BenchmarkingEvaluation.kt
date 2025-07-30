package benchmarking

import analyzeLog
import printLogSummary
import java.io.File

fun main() {
    evaluateBenchmarks()
}

private fun evaluateBenchmarks() {
    val logsPath = System.getProperty("usvm.logs")
    val logs = File(logsPath).listFiles()
    check(logs != null)
    for (log in logs) {
        val benchName = log.name.removeSuffix(".ansi")
        analyzeBenchResults(log, benchName)
    }
}

private fun analyzeBenchResults(logPath: File, name: String) {
    val summary = analyzeLog(logPath.toPath())
    val containsErrors = summary.problems.map { it.type != ProblemType.EXCEPTION }.any()
    if (containsErrors) {
        println("$name has problems")
    }
    printLogSummary(summary, System.out)
}
