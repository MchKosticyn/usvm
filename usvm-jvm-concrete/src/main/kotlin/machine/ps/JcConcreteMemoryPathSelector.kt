package machine.ps

import machine.state.JcConcreteState
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.PathNode
import org.usvm.UPathSelector
import org.usvm.machine.state.JcState

internal abstract class JcConcreteMemoryPathSelector(
    private val shouldChangePath: Boolean
): UPathSelector<JcState> {

    private lateinit var addNewState: ((JcState) -> Unit)

    private var fixedState: JcConcreteState? = null

    private var lastAddedBaseForkPoint: PathNode<JcInst>? = null

    private var lastAddedStates: MutableList<JcState>? = null

    private var backtrackedState: JcState? = null

    internal fun setAddStateAction(action: (JcState) -> Unit) {
        check(!this::addNewState.isInitialized)
        addNewState = action
    }

    private fun fixState(state: JcState) {
        println("picked state: ${state.id}")
        state as JcConcreteState
        state.concreteMemory.reset()
        fixedState = state
        lastAddedStates = null
        lastAddedBaseForkPoint = null
    }

    protected abstract fun chooseLastPickedState(relevantStates: List<JcState>): JcState

    protected abstract fun peekInternal(): JcState

    private val JcState.lastForkPoint: PathNode<JcInst>? get() {
        return if (forkPoints.depth > 0) forkPoints.statement else null
    }

    final override fun peek(): JcState {
        backtrackedState?.let {
            fixState(it)
            return it
        }

        val lastStates = lastAddedStates
        val lastForkPoint =
            if (shouldChangePath) lastAddedBaseForkPoint ?: fixedState?.lastForkPoint
            else lastAddedBaseForkPoint

        if (!lastStates.isNullOrEmpty() && lastForkPoint != null) {
            val relevantLastAddedStates =
                lastStates.filter { it.forkPoints.statement == lastForkPoint }
            val relevantStates =
                if (shouldChangePath && fixedState != null) relevantLastAddedStates + fixedState!!
                else relevantLastAddedStates
            if (relevantStates.isNotEmpty()) {
                val state = chooseLastPickedState(relevantStates)
                fixState(state)
                return state
            }
        }

        if (fixedState != null)
            return fixedState!!

        val state = peekInternal()
        fixState(state)
        return state
    }

    protected abstract fun addInternal(states: Collection<JcState>)

    final override fun add(states: Collection<JcState>) {
        addInternal(states)
        lastAddedStates = states.toMutableList()
    }

    protected abstract fun removeInternal(state: JcState)

    final override fun remove(state: JcState) {
        println("removed state: ${state.id}")
        check(fixedState === state)
        state as JcConcreteState
        val memory = state.concreteMemory
        backtrackedState = memory.kill()
        backtrackedState?.let(addNewState)
        removeInternal(state)
        if (state.callStack.isNotEmpty())
            lastAddedBaseForkPoint = state.forkPoints.statement
        lastAddedStates?.remove(state)
        fixedState = null
    }
}
