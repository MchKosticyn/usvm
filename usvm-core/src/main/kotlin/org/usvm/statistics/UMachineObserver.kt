package org.usvm.statistics

/**
 * Symbolic machine events observer.
 */
interface UMachineObserver<State> {

    /**
     * Called after symbolic machine starts running.
     */
    fun onMachineStarted() { }

    /**
     * Called before symbolic machine stops running.
     */
    fun onMachineStopped() { }

    /**
     * Called when the execution of the state is terminated (by exception or return).
     */
    fun onStateTerminated(state: State, stateReachable: Boolean) { }

    /**
     * Called on each symbolic execution step. If the state has forked, [forks] are not empty.
     */
    fun onState(parent: State, forks: Sequence<State>) { }

    /**
     * Called before each symbolic execution step on state peeked from path selector.
     */
    fun onStatePeeked(state: State) { }

    /**
     * Called when a critical machine fail occurred
     */
    fun onCriticalFail(state: State, exception: Throwable) { }
}

class CompositeUMachineObserver<State>(
    private val observers: List<UMachineObserver<State>>
) : UMachineObserver<State> {
    constructor(vararg observers: UMachineObserver<State>) : this(observers.toList())

    override fun onMachineStarted() {
        observers.forEach { it.onMachineStarted() }
    }

    override fun onMachineStopped() {
        observers.forEach { it.onMachineStopped() }
    }

    override fun onStateTerminated(state: State, stateReachable: Boolean) {
        observers.forEach { it.onStateTerminated(state, stateReachable) }
    }

    override fun onState(parent: State, forks: Sequence<State>) {
        observers.forEach { it.onState(parent, forks) }
    }

    override fun onStatePeeked(state: State) {
        observers.forEach { it.onStatePeeked(state) }
    }

    override fun onCriticalFail(state: State, exception: Throwable) {
        observers.forEach { it.onCriticalFail(state, exception) }
    }
}
