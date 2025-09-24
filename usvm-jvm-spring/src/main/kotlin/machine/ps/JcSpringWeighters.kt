package machine.ps

import machine.JcSpringAnalysisMode
import machine.JcSpringMachineOptions
import machine.ps.weighters.JcConcreteBacktrackWeighter
import machine.ps.weighters.JcSpringPathWeighter
import machine.ps.weighters.JcSpringEdgeCaseWeighter
import machine.ps.weighters.JcSpringRegressionSuite
import machine.ps.weighters.JcSpringUncoveredStateWeighter
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.machine.state.JcState
import org.usvm.machine.state.lastStmt
import org.usvm.ps.weighters.CombinedStateStableFloatWeighter
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.TransitiveCoverageZoneObserver

// TODO: fine tuning
private const val uncoveredStateWeighterNorm = 64f
private const val springPathWeighterNorm = 10.6f
private const val springEdgeCaseWeighterNorm = 21.8f

private const val springRegressionSuiteNorm = 0f // TODO: fine tuning

private const val concreteBacktrackWeighterNorm = 3.6f

internal fun createSpringWeighters(
    jcSpringMachineOptions: JcSpringMachineOptions,
    coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState>,
    shouldNormalize: Boolean = true
): JcConcreteMachineWeighters {
    val springAnalysisMode = jcSpringMachineOptions.springAnalysisMode
    val mainWeighterWithNorm = when (springAnalysisMode) {
        JcSpringAnalysisMode.EdgeCases -> JcSpringEdgeCaseWeighter() to springEdgeCaseWeighterNorm
        JcSpringAnalysisMode.RegressionSuite -> JcSpringRegressionSuite() to springRegressionSuiteNorm
    }

    val baseWeightersWithNorm = listOf(
        JcSpringPathWeighter(springAnalysisMode) to springPathWeighterNorm,
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
