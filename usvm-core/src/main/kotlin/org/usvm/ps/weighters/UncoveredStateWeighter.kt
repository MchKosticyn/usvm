package org.usvm.ps.weighters

import org.usvm.UState
import org.usvm.ps.StateWeighter
import org.usvm.statistics.CoverageStatistics

abstract class UncoveredStateWeighter<Method, Statement, in State : UState<*, Method, Statement, *, *, in State>>(
    coverageStatistics: CoverageStatistics<Method, Statement, in State>,
) : StateWeighterWithReport<State, Int>() {

    protected val uncoveredStatements: HashSet<Statement> = HashSet(coverageStatistics.getUncoveredStatements())

    init {
        coverageStatistics.addOnCoveredObserver { _, _, statement ->
            uncoveredStatements.remove(statement)
        }
    }
}
