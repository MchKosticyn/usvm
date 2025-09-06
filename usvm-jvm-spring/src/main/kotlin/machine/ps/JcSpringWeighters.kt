package machine.ps

import machine.JcSpringAnalysisMode
import machine.JcSpringMachineOptions
import machine.ps.weighters.JcConcreteBacktrackWeighter
import machine.ps.weighters.JcSpringPathWeighter
import machine.ps.weighters.JcSpringEdgeCaseWeighter
import machine.ps.weighters.JcSpringRegressionSuite
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.machine.state.JcState
import org.usvm.ps.weighters.CombinedStateStableIntWeighter
import org.usvm.ps.weighters.UncoveredStateWeighter
import org.usvm.statistics.CoverageStatistics

// TODO: fine tuning
private const val uncoveredStateWeighterNorm = 0.1f
private const val springPathWeighterNorm = 1f
private const val springEdgeCaseWeighterNorm = 1f
private const val springRegressionSuiteNorm = 1f
private const val concreteBacktrackWeighterNorm = 0.1f

internal fun createSpringWeighters(
    jcSpringMachineOptions: JcSpringMachineOptions,
    coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState>,
): JcConcreteMachineWeighters {
    val springAnalysisMode = jcSpringMachineOptions.springAnalysisMode
    val mainWeighterWithNorm = when (springAnalysisMode) {
        JcSpringAnalysisMode.EdgeCases -> JcSpringEdgeCaseWeighter() to springEdgeCaseWeighterNorm
        JcSpringAnalysisMode.RegressionSuite -> JcSpringRegressionSuite() to springRegressionSuiteNorm
    }

    val baseWeightersWithNorm = listOf(
        UncoveredStateWeighter(coverageStatistics) to uncoveredStateWeighterNorm,
        JcSpringPathWeighter(springAnalysisMode) to springPathWeighterNorm,
        mainWeighterWithNorm,
    )
    val (baseWeighters, baseWeightersNorm) = baseWeightersWithNorm.unzip()
    val baseWeighter = CombinedStateStableIntWeighter(baseWeighters, baseWeightersNorm)

    val eachPeekWeightersWithNorm = listOf(
        JcConcreteBacktrackWeighter() to concreteBacktrackWeighterNorm
    )
    val (eachPeekWeighters, eachPeekWeightersNorm) = eachPeekWeightersWithNorm.unzip()
    val eachPeekWeighter = CombinedStateStableIntWeighter(eachPeekWeighters, eachPeekWeightersNorm)

    return JcConcreteMachineWeighters(baseWeighter, eachPeekWeighter)
}
