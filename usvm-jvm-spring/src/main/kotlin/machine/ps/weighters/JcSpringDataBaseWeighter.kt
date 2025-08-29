package machine.ps.weighters

import machine.state.JcSpringState
import org.jacodb.api.jvm.cfg.JcBranchingInst
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcTerminatingInst
import org.usvm.machine.state.JcState
import org.usvm.ps.StateWeighter
import org.usvm.spring.api.SpringEngine

private val engineName = SpringEngine::class.java.simpleName

private abstract class Decider {
    abstract val weight: Int
    abstract fun decide(inst: JcInst): Boolean

    fun weight(inst: JcInst) = if (decide(inst)) weight else null
}

private object GoodsDecider: Decider() {

    val methodName = SpringEngine::markAsGoodPath.name

    // TODO: tune
    override val weight = 1

    override fun decide(inst: JcInst) =
        (inst as? JcCallInst)?.let {
            val method = inst.callExpr.method.method
            method.enclosingClass.simpleName == engineName
                    && method.name == methodName
        } == true
}

private object BadsDecider: Decider() {

    val methodName = SpringEngine::markAsBadPath.name

    // TODO: tune
    override val weight = -10

    override fun decide(inst: JcInst) =
        (inst as? JcCallInst)?.let {
            val method = inst.callExpr.method.method
            method.enclosingClass.simpleName == engineName
                    && method.name == methodName
        } == true
}

class JcSpringDataBaseWeighter : StateWeighter<JcState, Int> {

    private fun decide(inst: JcInst): Int {
        deciders.forEach { decider ->
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

        return history.fold(0) { weight, stmt -> weight + decide(stmt) }
    }

    companion object {
        private val deciders = listOf(
            GoodsDecider,
            BadsDecider
        )

        // TODO: tune
        private const val HISTORY_LIMIT = 500
    }
}
