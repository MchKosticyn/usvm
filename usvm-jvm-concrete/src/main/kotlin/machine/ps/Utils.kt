package machine.ps

import org.jacodb.api.jvm.cfg.JcBranchingInst
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcTerminatingInst
import org.usvm.machine.state.JcState
import java.io.PrintStream

internal val weightersLog = PrintStream(System.getProperty("usvm.log").replace(".ansi", "Weighters.log"))

private fun nextInst(inst: JcInst, insts: JcInstList<JcInst>) =
    when (inst) {
        // important to check goto first
        is JcGotoInst -> insts[inst.target.index]

        // return + throw + if + switch + goto!
        is JcTerminatingInst, is JcBranchingInst -> null

        else -> insts[inst.location.index + 1]
    }

fun collectFrame(state: JcState, historyLimit: Int? = null): List<JcInst> {
    val firstStmt = state.currentStatement
    val insts = firstStmt.location.method.instList
    val nodeStmts = state.pathNode.allStatements
    val history = (historyLimit?.let { nodeStmts.take(it) } ?: nodeStmts).toMutableList()

    var currStmt = nextInst(firstStmt, insts)
    while (currStmt != null) {
        history.add(currStmt)
        currStmt = nextInst(currStmt, insts)
    }

    return history
}
