package machine.ps
import machine.JcSpringTimeStatistics
import machine.state.JcSpringState
import org.usvm.UPathSelector
import org.usvm.machine.state.JcState
import kotlin.time.Duration

internal class JcStatePathTimeoutPathSelector(
    private val stats: JcSpringTimeStatistics,
    private val base: UPathSelector<JcState>,
    private val timeout: Duration,
) : UPathSelector<JcState> {
    override fun isEmpty(): Boolean = base.isEmpty()

    override fun peek(): JcState = base.peek()

    private fun checkTime(state: JcState): Boolean {
        state as JcSpringState
        val path = state.path ?: return true

        val pathLimit = timeout / state.handlerData.size
        val keyDuration = stats.getTimeSpentOnPath(path)
        return keyDuration <= pathLimit
    }

    override fun update(state: JcState) {
        if (checkTime(state)) {
            base.update(state)
            return
        }

        System.err.println("Path timeout exceeded: ${state.id}")
        base.remove(state)
    }

    override fun add(states: Collection<JcState>) {
        val checkedStates = mutableListOf<JcState>()
        for (state in states) {
            if (checkTime(state))
                checkedStates.add(state)
            else
                System.err.println("Path timeout exceeded: ${state.id}")
        }
        base.add(checkedStates)
    }

    override fun remove(state: JcState) = base.remove(state)
}
