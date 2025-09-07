package machine.ps

import org.usvm.algorithms.DeterministicPriorityCollection
import org.usvm.machine.logger
import org.usvm.machine.state.JcState
import org.usvm.ps.StateWeighter
import org.usvm.ps.weighters.stableAdd

internal class JcConcreteWeightedPathSelector(
    weighters: JcConcreteMachineWeighters
) : JcConcreteMemoryPathSelector(true) {
    private companion object {
        private const val TOP_COUNT = 10
        private const val WEIGHT_THRESHOLD = -100
    }

    private val baseWeighter: StateWeighter<JcState, Int> = weighters.baseWeighter
    private val eachPeekWeighter: StateWeighter<JcState, Int> = weighters.eachPeekWeighter

    private val priorityCollection = DeterministicPriorityCollection<JcState, Int>(Comparator.naturalOrder())

    override fun chooseLastPickedState(relevantStates: List<JcState>): JcState {
        val statesWithWeight = relevantStates.map { it to eachPeekWeighter.weight(it).stableAdd(baseWeighter.weight(it)) }
        val (bestState, bestWeight) = statesWithWeight.maxBy { it.second }
        logger.info { "chooseLastPickedState: bestWeight $bestWeight" }
        if (bestWeight < WEIGHT_THRESHOLD)
            return peekInternal()
        return bestState
    }

    override fun peekInternal(): JcState {
        val (state, weight) = priorityCollection.takeWithWeight(TOP_COUNT).maxBy { (state, weight) ->
            eachPeekWeighter.weight(state).stableAdd(weight)
        }
        logger.info { "picked state [${state.id}] with weight $weight" }
        return state
    }

    override fun addInternal(states: Collection<JcState>) {
        for (state in states) {
            priorityCollection.add(state, baseWeighter.weight(state))
        }
    }

    override fun removeInternal(state: JcState) {
        priorityCollection.remove(state)
    }

    override fun isEmpty(): Boolean {
        return priorityCollection.count == 0
    }

    override fun update(state: JcState) {
        priorityCollection.update(state, baseWeighter.weight(state))
    }
}
