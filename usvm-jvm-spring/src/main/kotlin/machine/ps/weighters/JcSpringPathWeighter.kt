package machine.ps.weighters

import machine.JcSpringAnalysisMode
import machine.state.JcSpringState
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcBranchingInst
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcTerminatingInst
import org.usvm.machine.state.JcState
import org.usvm.ps.StateWeighter
import org.usvm.spring.api.SpringEngine

private val engineName = SpringEngine::class.java.name

private abstract class JcCallInstWeighter {
    abstract val weight: Int
    abstract fun weightCall(callMethod: JcMethod): Boolean

    fun weight(inst: JcInst): Int? {
        if (inst !is JcCallInst)
            return null

        val callMethod = inst.callExpr.method.method
        return if (weightCall(callMethod)) weight else null
    }
}

private abstract class JcEngineCallInstWeighter : JcCallInstWeighter() {
    abstract val methodName: String
    final override fun weightCall(callMethod: JcMethod): Boolean =
        callMethod.enclosingClass.name == engineName && callMethod.name == methodName
}

private object GoodPathsWeighter: JcEngineCallInstWeighter() {
    override val methodName = SpringEngine::markAsGoodPath.name

    // TODO: tune
    override val weight = 1
}

private object BadPathsWeighter: JcEngineCallInstWeighter() {
    override val methodName = SpringEngine::markAsBadPath.name

    // TODO: tune
    override val weight = -10
}

private class EdgeCasesWeighter(
    springAnalysisMode: JcSpringAnalysisMode
): JcEngineCallInstWeighter() {
    override val methodName = SpringEngine::markAsEdgeCasePath.name

    // TODO: tune
    override val weight = when (springAnalysisMode) {
        JcSpringAnalysisMode.EdgeCases -> 100
        JcSpringAnalysisMode.RegressionSuite -> -10
    }
}

class JcSpringPathWeighter(
    springAnalysisMode: JcSpringAnalysisMode
) : StateWeighter<JcState, Int> {

    private companion object {
        // TODO: tune
        private const val HISTORY_LIMIT = 500

        private val DEFAULT_WEIGHTERS = listOf(
            GoodPathsWeighter,
            BadPathsWeighter
        )
    }

    private val instWeighters: List<JcCallInstWeighter> = DEFAULT_WEIGHTERS + EdgeCasesWeighter(springAnalysisMode)

    private fun weight(inst: JcInst): Int {
        for (decider in instWeighters) {
            decider.weight(inst)?.let { return it }
        }
        return 0
    }

    private fun nextInst(inst: JcInst, insts: JcInstList<JcInst>) =
        when(inst) {
            // important to check goto first
            is JcGotoInst -> insts[inst.target.index]

            // return + throw + if + switch + goto!
            is JcTerminatingInst, is JcBranchingInst -> null

            else -> insts[inst.location.index + 1]
        }

    override fun weight(state: JcState): Int {
        state as JcSpringState

        val firstStmt = state.currentStatement
        val insts = firstStmt.location.method.instList
        val history = state.pathNode.allStatements.take(HISTORY_LIMIT).toMutableList()

        var currStmt = nextInst(firstStmt, insts)
        while (currStmt != null) {
            history.add(currStmt)
            currStmt = nextInst(currStmt, insts)
        }

        return history.sumOf { weight(it) }
    }
}
