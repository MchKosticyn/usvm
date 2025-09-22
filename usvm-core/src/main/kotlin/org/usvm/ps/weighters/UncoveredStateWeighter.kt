package org.usvm.ps.weighters

import java.io.File
import org.usvm.UState
import org.usvm.ps.StateWeighter
import org.usvm.statistics.CoverageStatistics
import java.io.PrintStream

// TODO: delete #KEK
val weightersLogFile = File(System.getProperty("user.dir")).resolve("weighters.log").also {
    if (it.exists()) it.delete()
    it.createNewFile()
}

val weightersLog = PrintStream(weightersLogFile)// PrintStream("/Users/michael/Documents/Work/usvm/weighters.log")

class UncoveredStateWeighter<Method, Statement, in State : UState<*, Method, Statement, *, *, in State>>(
    coverageStatistics: CoverageStatistics<Method, Statement, in State>,
) : StateWeighter<State, Int> {

    private val uncoveredStatements: HashSet<Statement> = HashSet(coverageStatistics.getUncoveredStatements())

    init {
        coverageStatistics.addOnCoveredObserver { _, _, statement ->
            uncoveredStatements.remove(statement)
        }
    }

    override fun weight(state: State): Int {
        val result = state.pathNode.allStatements.count { it in uncoveredStatements }
        weightersLog.println("UncoveredStateWeighter: state = ${state.id}, weight = $result")
        return result
    }
}
