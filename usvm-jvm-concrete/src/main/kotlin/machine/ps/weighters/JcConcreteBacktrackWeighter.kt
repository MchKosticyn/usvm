package machine.ps.weighters

import machine.state.JcConcreteState
import org.usvm.machine.state.JcState
import org.usvm.ps.weighters.NormalizableWeighter
import org.usvm.ps.weighters.StateWeighterWithNorm
import org.usvm.ps.weighters.StateWeighterWithReport
import org.usvm.ps.weighters.WeightNormalizerType

class JcConcreteBacktrackWeighter() : StateWeighterWithReport<JcState, Int>(), NormalizableWeighter<JcState, Float> {

    override val weighterName = "ConcreteBacktrackWeighter"

    override fun weight(state: JcState) = -(state as JcConcreteState).concreteMemory.resetWeight()

    override fun normalize() =
        StateWeighterWithNorm.normalizeIntToFloat(this, MAX_WEIGHT, MIN_WEIGHT, WeightNormalizerType.NEGATIVE)

    companion object {
        private const val MAX_WEIGHT = 0
        private const val MIN_WEIGHT = -1_000_000
    }
}
