package machine.ps

import org.usvm.machine.state.JcState
import org.usvm.ps.weighters.StateWeighterWithReport

data class JcConcreteMachineWeighters(
    val baseWeighter: StateWeighterWithReport<JcState, Float>,
    val eachPeekWeighter: StateWeighterWithReport<JcState, Float>
)
