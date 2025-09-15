package machine.ps.weighters

import machine.state.JcConcreteState
import org.usvm.machine.state.JcState
import org.usvm.ps.StateWeighter
import org.usvm.ps.weighters.weightersLog

class JcConcreteBacktrackWeighter: StateWeighter<JcState, Int> {

    override fun weight(state: JcState): Int {
        state as JcConcreteState
        val result = -state.concreteMemory.resetWeight()
        weightersLog.println("JcConcreteBacktrackWeighter: state = ${state.id}, weight = $result")
        return result
    }
}
