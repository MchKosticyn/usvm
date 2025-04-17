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

    override fun peek(): JcState {
        if (fixedState != null)
            return fixedState as JcState
        val state = selector.peek()
        fixedState = state
        val memory = state.memory as JcConcreteMemory
        println("picked state: ${state.id}")
        memory.reset()
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
            fixedState = lastAddedStates.firstOrNull { !removedStateIds.contains(it.id) }
            (fixedState?.memory as? JcConcreteMemory)?.reset()
        } else {
            fixedState = null
        }
        println("removed state: ${state.id}")
    }
}
