package machine.ps

import machine.JcSpringAnalysisMode
import machine.JcSpringMachineOptions
import machine.ps.weighters.JcConcreteBacktrackWeighter
import machine.ps.weighters.JcForkTracesWeighter
import machine.ps.weighters.JcSpringPathWeighter
import machine.ps.weighters.JcSpringEdgeCaseWeighter
import machine.ps.weighters.JcSpringRegressionSuite
import machine.ps.weighters.JcSpringUncoveredStateWeighter
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.machine.state.JcState
import org.usvm.ps.weighters.CombinedStateStableFloatWeighter
import org.usvm.ps.weighters.ForkTracesHolder
import org.usvm.statistics.CoverageStatistics

private const val uncoveredStateWeighterNorm = 45f
private const val forkTracesWeighterNorm = 34.1f
private const val springPathWeighterNorm = 6.8f
private const val springEdgeCaseWeighterNorm = 11.6f

private const val springRegressionSuiteNorm = 0f // TODO: fine tuning

private const val concreteBacktrackWeighterNorm = 2.4f

internal fun createSpringWeighters(
    jcSpringMachineOptions: JcSpringMachineOptions,
    coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState>,
    tracesHolder: ForkTracesHolder<JcMethod, JcInst, JcState>,
    shouldNormalize: Boolean = true
): JcConcreteMachineWeighters {
    val springAnalysisMode = jcSpringMachineOptions.springAnalysisMode
    val mainWeighterWithNorm = when (springAnalysisMode) {
        JcSpringAnalysisMode.EdgeCases -> JcSpringEdgeCaseWeighter() to springEdgeCaseWeighterNorm
        JcSpringAnalysisMode.RegressionSuite -> JcSpringRegressionSuite() to springRegressionSuiteNorm
    }

    val baseWeightersWithNorm = listOf(
        JcSpringPathWeighter(springAnalysisMode) to springPathWeighterNorm,
        JcForkTracesWeighter(tracesHolder) to forkTracesWeighterNorm,
        mainWeighterWithNorm
    )
    val (baseWeighters, baseWeightersNorm) = baseWeightersWithNorm.unzip()

    val eachPeekWeightersWithNorm = listOf(
        JcSpringUncoveredStateWeighter(coverageStatistics) to uncoveredStateWeighterNorm,
        JcConcreteBacktrackWeighter() to concreteBacktrackWeighterNorm
    )
    val (eachPeekWeighters, eachPeekWeightersNorm) = eachPeekWeightersWithNorm.unzip()

    val baseWeighter: CombinedStateStableFloatWeighter<JcState>
    val eachPeekWeighter: CombinedStateStableFloatWeighter<JcState>
    if (shouldNormalize) {
        baseWeighter = CombinedStateStableFloatWeighter.withNorm(baseWeighters, baseWeightersNorm)
        eachPeekWeighter = CombinedStateStableFloatWeighter.withNorm(eachPeekWeighters, eachPeekWeightersNorm)
    } else {
        baseWeighter = CombinedStateStableFloatWeighter.fromInt(baseWeighters, baseWeightersNorm)
        eachPeekWeighter = CombinedStateStableFloatWeighter.fromInt(eachPeekWeighters, eachPeekWeightersNorm)
    }

    return JcConcreteMachineWeighters(baseWeighter, eachPeekWeighter)
}
