package org.usvm.ps.weighters

import org.usvm.UState
import org.usvm.statistics.UMachineObserver

class ForkTracesHolder<Method, Statement, State : UState<*, Method, Statement, *, *, State>>(
    private val stateFilter: (State) -> Boolean = { true }
) : UMachineObserver<State> {

    private val completedTraces: MutableMap<State, List<Statement>> = mutableMapOf()

    fun getCompletedTraces() = completedTraces.values

    private fun getForkStatements(state: State) =
        state.forkPoints.allStatements.map { it.statement }.toSet()

    fun getAfterForkStatements(state: State): List<Statement> {
        val forks = getForkStatements(state)
        val afterForks = mutableListOf<Statement>()
        state.pathNode.allStatements.reduce { prev, curr ->
            if (forks.contains(curr)) afterForks.add(prev)
            curr
        }
        return afterForks
    }

    override fun onStateTerminated(state: State, stateReachable: Boolean) {
        if (!stateReachable || !stateFilter(state)) return
        completedTraces[state] = getAfterForkStatements(state)
    }
}
