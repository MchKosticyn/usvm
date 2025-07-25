package machine.ps

import org.usvm.algorithms.DeterministicPriorityCollection
import org.usvm.machine.state.JcState
import org.usvm.ps.StateWeighter
import org.usvm.ps.WeightedPathSelector

class JcConcreteWeightedPathSelector(
    private val weighter: StateWeighter<JcState, Int>
) : WeightedPathSelector<JcState, Int>(
    { DeterministicPriorityCollection(Comparator.naturalOrder()) },
    weighter
) {
    private var fixedState: JcState? = null
    private var deletedState: JcState? = null

    private var lastAddedStates: MutableList<JcState>? = null

    private fun fixState(state: JcState) {
        fixedState = state
        lastAddedStates = null
        deletedState = null
    }

    override fun peek(): JcState {
        if (lastAddedStates != null && lastAddedStates!!.isNotEmpty()) {
            val lastForkPoint = (fixedState ?: deletedState!!).forkPoints.statement
            val relevantLastAddedStates =
                lastAddedStates!!.filter { it.forkPoints.statement == lastForkPoint }
            val relevantStates =
                if (fixedState != null) relevantLastAddedStates + fixedState!!
                else relevantLastAddedStates
            // TODO: cache weight?
            val state = relevantStates.maxBy { weighter.weight(it) }
            fixState(state)
            return state
        }

        if (fixedState != null) {
            return fixedState!!
        }

        val state = super.peek()
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
