package machine.ps.weighters

import machine.state.JcSpringState
import org.usvm.machine.state.JcState
import org.usvm.ps.weighters.NormalizableWeighter
import org.usvm.ps.weighters.StateWeighterWithNorm
import org.usvm.ps.weighters.StateWeighterWithReport
import org.usvm.ps.weighters.WeightNormalizerType

class JcSpringEdgeCaseWeighter() : StateWeighterWithReport<JcState, Int>(), NormalizableWeighter<JcState, Float> {

    override val weighterName = "SpringEdgeCaseWeighter"

    private companion object {
        private const val GOOD_WEIGHT = 10
        private const val BAD_WEIGHT = 0
    }

    override fun weight(state: JcState): Int {
        state as JcSpringState
        // TODO: check validation errors
        return if (state.isExceptional) GOOD_WEIGHT else BAD_WEIGHT
    }


    // TODO: it may be more complex
    override fun normalize() =
        StateWeighterWithNorm.normalizeIntToFloat(this, GOOD_WEIGHT, BAD_WEIGHT, WeightNormalizerType.POSITIVE)
}
