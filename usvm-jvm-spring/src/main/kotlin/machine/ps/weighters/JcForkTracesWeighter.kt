package machine.ps.weighters

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.machine.state.JcState
import org.usvm.ps.weighters.ForkTracesHolder
import org.usvm.ps.weighters.NormalizableWeighter
import org.usvm.ps.weighters.StateWeighterWithNorm
import org.usvm.ps.weighters.StateWeighterWithReport
import org.usvm.ps.weighters.WeightNormalizerType

class JcForkTracesWeighter(
    val forkTracesHolder: ForkTracesHolder<JcMethod, JcInst, JcState>
) : StateWeighterWithReport<JcState, Int>(), NormalizableWeighter<JcState, Float> {

    override val weighterName = "JcForkTracesWeighter"

    private fun similarity(completedTrace: List<JcInst>, activeTrace: List<JcInst>): Int {
        val lastActiveForkInst = activeTrace.lastOrNull() ?: return 0
        var completedIndex = completedTrace.indexOf(lastActiveForkInst)
        return activeTrace.foldRight(0) { inst, sum ->
            if (completedTrace.getOrNull(completedIndex) != inst) return sum
            completedIndex--
            sum - 1
        }
    }

    override fun weight(state: JcState): Int {
        val currTrace = forkTracesHolder.getAfterForkStatements(state)
        val completedTraces = forkTracesHolder.getCompletedTraces()
        return completedTraces.minOfOrNull { trace -> similarity(trace, currTrace) } ?: 0
    }

    override fun normalize() =
        StateWeighterWithNorm.normalizeIntToFloat(this, MAX_WEIGHT, MIN_WEIGHT, WeightNormalizerType.NEGATIVE)

    companion object {

        // TODO: tune
        const val MAX_WEIGHT = 0
        const val MIN_WEIGHT = -150
    }
}
