package machine.ps.weighters

import machine.ps.collectFrame
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.machine.state.JcState
import org.usvm.ps.weighters.NormalizableWeighter
import org.usvm.ps.weighters.StateWeighterWithNorm
import org.usvm.ps.weighters.UncoveredStateWeighter
import org.usvm.ps.weighters.WeightNormalizerType
import org.usvm.statistics.CoverageStatistics

class JcSpringUncoveredStateWeighter(
    coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState>
) : UncoveredStateWeighter<JcMethod, JcInst, JcState>(coverageStatistics), NormalizableWeighter<JcState, Float> {

    override val weighterName = "JcSpringUncoveredStateWeighter"

    override fun weight(state: JcState) = collectFrame(state).count { it in uncoveredStatements }

    // TODO: it may be more complex
    override fun normalize() =
        StateWeighterWithNorm.normalizeIntToFloat(this, MAX_WEIGHT, MIN_WEIGHT, WeightNormalizerType.POSITIVE)

    companion object {

        // TODO: tune
        const val MAX_WEIGHT = 30
        const val MIN_WEIGHT = 0
    }
}
