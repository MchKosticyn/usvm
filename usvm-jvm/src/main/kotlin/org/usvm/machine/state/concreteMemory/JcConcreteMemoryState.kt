package org.usvm.machine.state.concreteMemory

private enum class State {
    Mutable,
    MutableWithEffect,
    Immutable,
    Dead
}

internal class JcConcreteMemoryState private constructor(
    private var state: State
) {

    internal constructor() : this(State.Mutable)

    fun isWritable(): Boolean {
        return when (state) {
            State.Mutable -> true
            State.MutableWithEffect -> true
            else -> false
        }
    }

    fun isDead(): Boolean {
        return state == State.Dead
    }

    fun isAlive(): Boolean {
        return !isDead()
    }

    fun isMutableWithEffect(): Boolean {
        return state == State.MutableWithEffect
    }

    fun makeImmutable() {
        state = State.Immutable
    }

    fun makeMutable() {
        state = State.Mutable
    }

    fun makeMutableWithEffect() {
        state = State.MutableWithEffect
    }

    fun makeDead() {
        state = State.Dead
    }

    fun copy(): JcConcreteMemoryState {
        return JcConcreteMemoryState(state)
    }
}
