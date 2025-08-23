package machine.ps

import org.usvm.UPathSelector
import org.usvm.machine.state.JcState

internal class JcConcreteWrappingPathSelector(
    private val selector: UPathSelector<JcState>
) : JcConcreteMemoryPathSelector(false) {

    override fun isEmpty(): Boolean {
        return selector.isEmpty()
    }

    override fun chooseLastPickedState(relevantStates: List<JcState>): JcState {
        return relevantStates.first()
    }

    override fun peekInternal(): JcState {
        return selector.peek()
    }

    override fun addInternal(states: Collection<JcState>) {
        selector.add(states)
    }

    override fun update(state: JcState) {
        selector.update(state)
    }

    override fun removeInternal(state: JcState) {
        selector.remove(state)
    }
}
