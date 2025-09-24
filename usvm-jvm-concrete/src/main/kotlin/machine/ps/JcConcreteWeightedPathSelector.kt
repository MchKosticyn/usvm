package machine.ps

import org.usvm.algorithms.DeterministicPriorityCollection
import org.usvm.machine.state.JcState
import org.usvm.ps.weighters.StableFloatArithmetic
import org.usvm.ps.weighters.StateWeighterWithReport
import org.usvm.ps.weighters.WeighterReport

private class CommonJcStateReport(
    val weight: Float,
    val state: JcState,
    val baseWeighterReport: WeighterReport<Float>,
    val eachPeekWeighterReport: WeighterReport<Float>
) {
    fun report2Message() = buildString {
        append("\n\t")
        append("[id: ${state.id}] ")
        append("[w: ${weight}] | ")
        append("${baseWeighterReport.report2String()} | ${eachPeekWeighterReport.report2String()}")
    }
}

internal class JcConcreteWeightedPathSelector(
    weighters: JcConcreteMachineWeighters
) : JcConcreteMemoryPathSelector(true) {
    private companion object {
        private const val TOP_COUNT = 50
        private const val WEIGHT_THRESHOLD = -5f
    }

    private val baseWeighter: StateWeighterWithReport<JcState, Float> = weighters.baseWeighter
    private val eachPeekWeighter: StateWeighterWithReport<JcState, Float> = weighters.eachPeekWeighter

    private val priorityCollection = DeterministicPriorityCollection<JcState, WeighterReport<Float>>(
        object : Comparator<WeighterReport<Float>> {
            override fun compare(left: WeighterReport<Float>, right: WeighterReport<Float>) =
                left.weight.compareTo(right.weight)
        }
    )

    override fun chooseLastPickedState(relevantStates: List<JcState>) = with(StableFloatArithmetic) {
        val statesWithReport = relevantStates.map { state ->
            val pReport = eachPeekWeighter.weightWithReport(state)
            val bReport = baseWeighter.weightWithReport(state)
            CommonJcStateReport(pReport.weight.plusTo(bReport.weight), state, bReport, pReport)
        }.sortedByDescending(CommonJcStateReport::weight)
        val bestState = statesWithReport.first()
        val bestWeight = bestState.weight

        val message = buildString {
            append("chooseLastPickedState: ")
            statesWithReport.forEach { report -> append(report.report2Message()) }
        }
        weightersLog.println(message)

        if (bestWeight < WEIGHT_THRESHOLD) peekInternal() else bestState.state
    }

    override fun peekInternal() = with(StableFloatArithmetic) {
        val statesWithReport = priorityCollection.takeWithWeight(TOP_COUNT).map { (state, report) ->
            val pReport = eachPeekWeighter.weightWithReport(state)
            CommonJcStateReport(pReport.weight.plusTo(report.weight), state, report, pReport)
        }.sortedByDescending(CommonJcStateReport::weight)
        val bestState = statesWithReport.first()

        val message = buildString {
            append("peekInternal: ")
            statesWithReport.forEach { report -> append(report.report2Message()) }
        }
        weightersLog.println(message)

        bestState.state
    }

    override fun addInternal(states: Collection<JcState>) {
        for (state in states) {
            weightersLog.println("add internal state: ${state.id}")
            priorityCollection.add(state, baseWeighter.weightWithReport(state))
        }
    }

    override fun removeInternal(state: JcState) {
        weightersLog.println("remove internal state: ${state.id}")
        priorityCollection.remove(state)
    }

    override fun isEmpty(): Boolean {
        return priorityCollection.count == 0
    }

    override fun update(state: JcState) {
        priorityCollection.update(state, baseWeighter.weightWithReport(state))
    }
}
