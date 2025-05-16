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

    private lateinit var addNewState: ((JcState) -> Unit)

    internal fun setAddStateAction(action: (JcState) -> Unit) {
        check(!this::addNewState.isInitialized)
        addNewState = action
    }

    override fun isEmpty(): Boolean {
        return selector.isEmpty()
    }

    private fun fixState(state: JcState) {
        check(!removedStateIds.contains(state.id))
        fixedState = state
        println("picked state: ${state.id}")
        val memory = state.memory as JcConcreteMemory
        memory.reset()
    }

    override fun peek(): JcState {
        if (fixedState != null) {
            check(!removedStateIds.contains(fixedState!!.id))
            return fixedState as JcState
        }
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
        check(!removedStateIds.contains(state.id))
        val memory = state.memory as JcConcreteMemory
        val backtrackedState = memory.kill()
        removedStateIds.add(state.id)
        selector.remove(state)
        if (state.callStack.isNotEmpty()) {
            if (backtrackedState != null)
                addNewState(backtrackedState)
            val newState = backtrackedState ?: lastAddedStates.firstOrNull { !removedStateIds.contains(it.id) }
            if (newState != null)
                fixState(newState)
            else
                fixedState = null
        } else {
            fixedState = null
        }
        println("removed state: ${state.id}")
    }
}
