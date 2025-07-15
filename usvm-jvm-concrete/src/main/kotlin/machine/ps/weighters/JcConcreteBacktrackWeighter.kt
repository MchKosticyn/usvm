package machine.ps.weighters

import machine.state.JcConcreteState
import org.usvm.ps.StateWeighter

class JcConcreteBacktrackWeighter: StateWeighter<JcConcreteState, Int> {

    override fun weight(state: JcConcreteState): Int {
        return - state.concreteMemory.resetWeight()
    }
}
