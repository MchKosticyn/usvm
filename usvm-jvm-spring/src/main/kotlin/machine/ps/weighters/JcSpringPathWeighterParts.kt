package machine.ps.weighters

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.machine.state.JcState
import org.usvm.ps.weighters.NormalizableWeighter
import org.usvm.ps.weighters.StableIntArithmetic
import org.usvm.ps.weighters.StateWeighterWithNorm
import org.usvm.ps.weighters.StateWeighterWithReport
import org.usvm.ps.weighters.WeightNormalizerType
import org.usvm.ps.weighters.WeighterReport
import org.usvm.spring.api.SpringEngine
import util.isSpringEngineMethod

enum class CallInstWeighterType(
    val weighterName: String
) {
    GOOD_PATH("GoodPathsWeighter"),
    BAD_PATH("BadPathsWeighter"),
    EDGE_CASES("EdgeCasesWeighter"),
    REGRESSION("RegressionWeighter")
}

private class CallInstWeighterReport<Weight>(
    override val weight: Weight,
    type: CallInstWeighterType
): WeighterReport<Weight>() {
    override val weighterName = type.weighterName
}

abstract class JcCallInstWeighterStats {
    abstract val weight: Int
    abstract val weighterType: CallInstWeighterType
    abstract val normalizerType: WeightNormalizerType

    abstract val maxActivations: Int // to calculate max summary weight of weighter

    val maxValue by lazy { if (weight < 0) 0 else maxActivations * weight }
    val minValue by lazy { if (weight < 0) maxActivations * weight else 0 }
}

abstract class JcCallInstWeighter: StateWeighterWithReport<JcState, Int>(), NormalizableWeighter<JcState, Float> {
    abstract val stats: JcCallInstWeighterStats
    abstract fun weightCall(callMethod: JcMethod): Boolean

    override val weighterName by lazy { stats.weighterType.weighterName }

    private var activations = 0

    fun update(inst: JcInst) {
        if (inst is JcCallInst && weightCall(inst.callExpr.method.method)) activations++
    }

    fun reset() { activations = 0 }

    override fun weight(state: JcState) = with(StableIntArithmetic) {
        min(activations, stats.maxActivations).mulTo(stats.weight)
    }

    override fun weightWithReport(state: JcState): WeighterReport<Int> =
        CallInstWeighterReport(weight(state), stats.weighterType)

    override fun normalize() =
        StateWeighterWithNorm.normalizeIntToFloat(this, stats.maxValue, stats.minValue, stats.normalizerType)
}

abstract class JcEngineCallInstWeighter : JcCallInstWeighter() {
    abstract val methodName: String
    final override fun weightCall(callMethod: JcMethod): Boolean =
        callMethod.isSpringEngineMethod && callMethod.name == methodName
}

object GoodPathsWeighter: JcEngineCallInstWeighter() {
    override val methodName = SpringEngine::markAsGoodPath.name

    override val stats = object : JcCallInstWeighterStats() {
        override val weight = 63
        override val maxActivations = 20
        override val normalizerType = WeightNormalizerType.POSITIVE
        override val weighterType = CallInstWeighterType.GOOD_PATH
    }
}

object BadPathsWeighter: JcEngineCallInstWeighter() {
    override val methodName = SpringEngine::markAsBadPath.name

    override val stats = object : JcCallInstWeighterStats() {
        override val weight = -265
        override val maxActivations = 10
        override val normalizerType = WeightNormalizerType.NEGATIVE
        override val weighterType = CallInstWeighterType.BAD_PATH
    }
}

object EdgeCasesWeighter: JcEngineCallInstWeighter() {
    override val methodName = SpringEngine::markAsEdgeCasePath.name

    override val stats = object : JcCallInstWeighterStats() {
        override val weight = 672
        override val maxActivations = 10
        override val normalizerType = WeightNormalizerType.POSITIVE
        override val weighterType = CallInstWeighterType.EDGE_CASES
    }
}

object RegressionSuiteWeighter: JcEngineCallInstWeighter() {
    override val methodName = SpringEngine::markAsEdgeCasePath.name

    // TODO: tune
    override val stats = object : JcCallInstWeighterStats() {
        override val weight = -32
        override val maxActivations = 10
        override val normalizerType = WeightNormalizerType.NEGATIVE
        override val weighterType = CallInstWeighterType.REGRESSION
    }
}
