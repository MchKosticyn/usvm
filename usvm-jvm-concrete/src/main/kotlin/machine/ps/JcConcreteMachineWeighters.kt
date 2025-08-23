package machine.ps

import org.usvm.machine.state.JcState
import org.usvm.ps.StateWeighter

data class JcConcreteMachineWeighters(
    val baseWeighter: StateWeighter<JcState, Int>,
    val eachPeekWeighter: StateWeighter<JcState, Int>
)
