package machine.ps.weighters

import machine.JcSpringAnalysisMode
import machine.ps.collectFrame
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.machine.state.JcState
import org.usvm.ps.weighters.CombinedWeighterReport
import org.usvm.ps.weighters.NormalizableWeighter
import org.usvm.ps.weighters.StableFloatArithmetic
import org.usvm.ps.weighters.StableIntArithmetic
import org.usvm.ps.weighters.StateWeighterWithReport
import org.usvm.ps.weighters.WeighterReport

private class SpringPathWeighterReport<Weight>(
    override val weight: Weight,
    override val weighterName: String,
    val weightersStats: Map<CallInstWeighterType, Int>
) : WeighterReport<Weight>() {
    override fun report2String(): String {
        val stats = weightersStats.map { (type, count) -> "${type.weighterName}: $count" }.joinToString(", ")
        return "$weighterName $weight {$stats}"
    }
}

class JcSpringPathWeighter(
    springAnalysisMode: JcSpringAnalysisMode
) : StateWeighterWithReport<JcState, Int>(), NormalizableWeighter<JcState, Float> {

    override val weighterName = NAME

    private val instWeighters: List<JcCallInstWeighter> = listOf(GoodPathsWeighter, BadPathsWeighter) +
            when (springAnalysisMode) {
                JcSpringAnalysisMode.EdgeCases -> EdgeCasesWeighter

                JcSpringAnalysisMode.RegressionSuite -> RegressionSuiteWeighter // TODO: fine tuning
            }

    private fun update(inst: JcInst) = instWeighters.forEach { it.update(inst) }
    private fun reset() = instWeighters.forEach(JcCallInstWeighter::reset)

    private fun updateByHistory(state: JcState) {
        collectFrame(state).forEach { update(it) }
    }

    override fun weight(state: JcState) = weightWithReport(state).weight

    override fun weightWithReport(state: JcState) = with(StableIntArithmetic) {
        reset()
        updateByHistory(state)

        val reports = mutableListOf<WeighterReport<Int>>()
        val weight = instWeighters.fold(zero) { sum, weighter ->
            val report = weighter.weightWithReport(state)
            reports.add(report)
            report.weight.plusTo(sum)
        }

        CombinedWeighterReport(weight, weighterName, reports)
    }

    override fun normalize() = object : StateWeighterWithReport<JcState, Float>() {
        override val weighterName = "$NAME (norm.)"

        private fun Float.finalNorm() = StableFloatArithmetic.div(this, instWeighters.count().toFloat())

        override fun weight(state: JcState) = with(StableFloatArithmetic) {
            reset()
            updateByHistory(state)

            instWeighters.fold(zero) { sum, weighter -> weighter.normalize().weight(state).plusTo(sum) }.finalNorm()
        }

        override fun weightWithReport(state: JcState) = with(StableFloatArithmetic) {
            reset()
            updateByHistory(state)

            val reports = mutableListOf<WeighterReport<Float>>()
            val weight = instWeighters.fold(zero) { sum, weighter ->
                val report = weighter.normalize().weightWithReport(state)
                reports.add(report)
                report.weight.plusTo(sum)
            }.finalNorm()

            CombinedWeighterReport(weight, weighterName, reports)
        }
    }

    private companion object {
        // TODO: tune
        private const val HISTORY_LIMIT = 10000

        private const val NAME = "JcSpringPathWeighter"
    }
}
