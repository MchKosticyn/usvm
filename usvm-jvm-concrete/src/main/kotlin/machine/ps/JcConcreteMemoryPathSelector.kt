package machine.ps

import machine.state.JcConcreteState
import org.usvm.UPathSelector
import org.usvm.machine.state.JcState

internal class JcConcreteMemoryPathSelector(
    private val selector: UPathSelector<JcState>
) : UPathSelector<JcState> {

    private var fixedState: JcConcreteState? = null

    private lateinit var addNewState: ((JcState) -> Unit)

    internal fun setAddStateAction(action: (JcState) -> Unit) {
        check(!this::addNewState.isInitialized)
        addNewState = action
    }

    override fun isEmpty(): Boolean {
        return selector.isEmpty()
    }

    private fun fixState(state: JcState) {
        state as JcConcreteState
        fixedState = state
        println("picked state: ${state.id}")
        state.concreteMemory.reset()
    }

    override fun peek(): JcState {
        if (fixedState != null)
            return fixedState!!

        val state = selector.peek()
        fixState(state)
        return state
    }

    override fun update(state: JcState) {
        selector.update(state)
    }

    override fun add(states: Collection<JcState>) {
        selector.add(states)
    }

    override fun remove(state: JcState) {
        check(fixedState === state)
        state as JcConcreteState
        val memory = state.concreteMemory
        val backtrackedState = memory.kill()
        selector.remove(state)
        if (state.callStack.isNotEmpty() && backtrackedState != null) {
            addNewState(backtrackedState)
            fixState(backtrackedState)
            return
        }
        fixedState = null
        println("removed state: ${state.id}")
    }
}
