package machine.ps.weighters

import machine.state.JcSpringState
import org.usvm.machine.state.JcState
import org.usvm.ps.StateWeighter
import org.usvm.ps.weighters.NormalizableWeighter
import org.usvm.ps.weighters.SingleWeighterReport
import org.usvm.ps.weighters.StateWeighterWithNorm
import org.usvm.ps.weighters.StateWeighterWithReport
import org.usvm.ps.weighters.WeightNormalizerType
import org.usvm.ps.weighters.WeighterReport

class JcSpringRegressionSuite() : StateWeighterWithReport<JcState, Int>(), NormalizableWeighter<JcState, Float> {

    override val weighterName = "SpringRegressionSuite"

    override fun weight(state: JcState): Int {
        state as JcSpringState
        // TODO: implement
        return 0
    }

    // TODO: it may be more complex
    override fun normalize() = StateWeighterWithNorm.normalizeIntToFloat(this, 1, 0, WeightNormalizerType.POSITIVE)
}
