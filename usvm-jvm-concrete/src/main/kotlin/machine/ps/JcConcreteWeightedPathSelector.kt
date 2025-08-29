package machine.ps

import org.usvm.algorithms.DeterministicPriorityCollection
import org.usvm.machine.logger
import org.usvm.machine.state.JcState
import org.usvm.ps.StateWeighter
import org.usvm.ps.WeightedPathSelector
import org.usvm.ps.weighters.stableAdd

class JcConcreteWeightedPathSelector(
    private val baseWeighter: StateWeighter<JcState, Int>,
    private val eachPeekWeighter: StateWeighter<JcState, Int>
) : WeightedPathSelector<JcState, Int>(
    { DeterministicPriorityCollection(Comparator.naturalOrder()) },
    baseWeighter
) {
    private companion object {
        private const val TOP_COUNT = 10
    }

    private val statesCollection get() = priorityCollection as DeterministicPriorityCollection<JcState, Int>

    private var fixedState: JcState? = null
    private var deletedState: JcState? = null

    private var lastAddedStates: MutableList<JcState>? = null

    private fun fixState(state: JcState) {
        fixedState = state
        lastAddedStates = null
        deletedState = null
    }

    override fun peek(): JcState {
        val lastStates = lastAddedStates
        if (!lastStates.isNullOrEmpty()) {
            val lastForkPoint = (fixedState ?: deletedState!!).forkPoints.statement
            val relevantLastAddedStates =
                lastStates.filter { it.forkPoints.statement == lastForkPoint }
            val relevantStates =
                if (fixedState != null) relevantLastAddedStates + fixedState!!
                else relevantLastAddedStates
            // TODO: cache weight?
            val state = relevantStates.maxBy { eachPeekWeighter.weight(it).stableAdd(baseWeighter.weight(it)) }
            fixState(state)
            return state
        }

        if (fixedState != null)
            return fixedState!!

        val (state, weight) = statesCollection.takeWithWeight(TOP_COUNT).maxBy { (state, weight) ->
            eachPeekWeighter.weight(state).stableAdd(weight)
        }

        logger.info { "picked state [${state.id}] with weight $weight" }
        fixState(state)
        return state
    }

    override fun add(states: Collection<JcState>) {
        super.add(states)
        lastAddedStates = states.toMutableList()
    }

    override fun remove(state: JcState) {
        check(fixedState === state)
        lastAddedStates?.remove(state)
        fixedState = null
        deletedState = state
        super.remove(state)
    }
}
