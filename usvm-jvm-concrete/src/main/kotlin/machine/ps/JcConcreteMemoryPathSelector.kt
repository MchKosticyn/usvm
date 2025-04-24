package machine.ps

import machine.concreteMemory.JcConcreteMemory
import org.usvm.StateId
import org.usvm.UPathSelector
import org.usvm.machine.state.JcState

internal class JcConcreteMemoryPathSelector(
    private val selector: UPathSelector<JcState>
) : UPathSelector<JcState> {

    private var fixedState: JcState? = null

    private var lastAddedStates: List<JcState> = emptyList()

    private val removedStateIds = mutableSetOf<StateId>()

    override fun isEmpty(): Boolean {
        return selector.isEmpty()
    }

    private fun fixState(state: JcState) {
        fixedState = state
        println("picked state: ${state.id}")
        val memory = state.memory as JcConcreteMemory
        memory.reset()
    }

    override fun peek(): JcState {
        if (fixedState != null)
            return fixedState as JcState
        val state = selector.peek()
        fixState(state)
        return state
    }

    override fun update(state: JcState) {
        selector.update(state)
    }

    override fun add(states: Collection<JcState>) {
        selector.add(states)
        lastAddedStates = states.toList()
    }

    override fun remove(state: JcState) {
        check(fixedState === state)
        (state.memory as JcConcreteMemory).kill()
        removedStateIds.add(state.id)
        selector.remove(state)
        if (state.callStack.isNotEmpty()) {
            val newState = lastAddedStates.firstOrNull { !removedStateIds.contains(it.id) }
            if (newState != null)
                fixState(newState)
        } else {
            fixedState = null
        }
        println("removed state: ${state.id}")
    }
}
