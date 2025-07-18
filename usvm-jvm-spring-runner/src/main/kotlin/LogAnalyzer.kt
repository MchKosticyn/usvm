import org.jacodb.util.io.writeString
import java.io.File
import java.io.PrintStream
import java.nio.file.Path

enum class ProblemType {
    EXCEPTION,
    WARNING,
    ASSERT
}

data class Problem(
    val type: ProblemType,
    val line: Int,
    val description: String,
    val stateId: Int,
    val path: String?
)

data class ForkPoint(
    val methodName: String,
    val line: Int,
    val description: String,
    var wasKilled: Boolean
)

data class LogSummary(
    val problems: List<Problem>,
    val forkPoints: List<ForkPoint>,
    val coverage: Int
)

private val DIGITS = "\\d+".toRegex()
private val STACK_DEPTH_PREFIX = "<\\|\\d+\\|>".toRegex()

fun removeStackDepth(line: String): String {
    return line.replace(STACK_DEPTH_PREFIX, "")
}

fun forkPointToString(forkPoint: ForkPoint): String {
    val killedText = if (forkPoint.wasKilled) "killed" else ""
    return "${forkPoint.line}: ${forkPoint.methodName} $killedText\n"
}

fun problemToString(problem: Problem): String {
    return "-----------\nType: ${problem.type.name}\nIn log line: ${problem.line}\nHappened in state ${problem.stateId} with path ${problem.path}\nLine content: ${problem.description}\n"
}

fun analyzeLog(logPath: Path): LogSummary {
    val log = logPath.toFile()
    val statePaths = HashMap<Int, String?>()
    val foundProblems = ArrayList<Problem>()
    val forkPoints = ArrayList<ForkPoint>()
    var currentState = 0
    var lineNumber = 0
    var beforePrintPath = false
    var beforeCoverage = false
    var coverage = 0

    log.forEachLine {
        lineNumber++
        val line = removeStackDepth(it)

        if (line.startsWith("picked state: ")) {
            currentState = DIGITS.find(it)!!.value.toInt()
        }

        if (line.startsWith("\u001B[34mForked on method ")) {
            forkPoints.add(ForkPoint(
                methodName = line.substring(22),
                line = lineNumber,
                description = line,
                wasKilled = false
            ))
        }

        if (line.startsWith("removed state: ")) {
            // Stacktrace should be greater than 50 really
            val latestForkPoint = forkPoints.lastOrNull()
            if (latestForkPoint != null && latestForkPoint.line - lineNumber < 50) {
                latestForkPoint.wasKilled = true
            }
        }

        val path: String? = statePaths[currentState]

        if (line.startsWith("exception thrown") && !line.contains("java.lang.Throwable")) {
            foundProblems.add(Problem(ProblemType.EXCEPTION, lineNumber, line, currentState, path))
        } else if (line.contains("Assert failed: ")) {
            foundProblems.add(Problem(ProblemType.ASSERT, lineNumber, line, currentState, path))
        } else if (line.contains("[Warning!]")) {
            foundProblems.add(Problem(ProblemType.WARNING, lineNumber, line, currentState, path))
        } else if (line.contains("\u001B[36m[USVM] starting to analyze path")) {
            beforePrintPath = true
        } else if (line.startsWith("\u001B[36m") && beforePrintPath) {
            statePaths[currentState] = line
            beforePrintPath = false
        } else if (line.startsWith(" Coverage, %")) {
            beforeCoverage = true
        } else if (beforeCoverage) {
            coverage = line
                .split(" ")
                .first { it.isNotEmpty() }
                .toInt()
            beforeCoverage = false
        }
        else if (line.startsWith("\u001B[34m[")) {
            val digits = DIGITS.findAll(line.substring(5)).toList()
            val from = digits[0].value.toInt()
            val to = digits[2].value.toInt()
            statePaths[to] = statePaths[from]
        }
    }

    if (foundProblems.isEmpty()) {
        println("[Analyzer] No problems found during execution\n")
    } else {
        println("[Analyzer] Some problems while execution occurred (${foundProblems.size})\n")
    }

    return LogSummary(foundProblems, forkPoints, coverage)
}

fun printLogSummary(summary: LogSummary, output: PrintStream) {
    output.writeString("Analyzer report\n")
    output.writeString("Coverage: ${summary.coverage}\n")
    summary.problems.filter { it.type != ProblemType.EXCEPTION }.groupBy { it.type }.forEach { t ->
        output.writeString("Problems of type ${t.key}\n")
        t.value.forEach { p -> output.writeString(problemToString(p)) }
    }
}
