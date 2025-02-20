package org.usvm.machine.state.concreteMemory.ps

import org.jacodb.api.jvm.JcClassType
import org.usvm.UConcreteHeapRef
import org.usvm.UHeapRef
import org.usvm.UPathSelector
import org.usvm.machine.state.JcState
import org.usvm.machine.state.concreteMemory.JcConcreteMemory

class JcConcreteMemoryPathSelector(
    private val selector: UPathSelector<JcState>
) : UPathSelector<JcState> {

    private var fixedState: JcState? = null

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
    }

    // TODO: govnokod
    private fun getConcreteValue(state: JcState, expr: UConcreteHeapRef) : Any? {
        if (expr.address == 0) return "null"
        val type = state.ctx.stringType as JcClassType
        return (state.memory as JcConcreteMemory).concretize(state, expr, expr as UHeapRef, type)
    }

    private fun printSpringTestSummary(state: JcState) {
        state.callStack.push(state.entrypoint, state.entrypoint.instList[0])
        val userDefinedValues = state.userDefinedValues
        userDefinedValues.forEach {
            val ref = state.models[0].eval(it.value.first)
            var value = ref.toString()

            if (ref is UConcreteHeapRef)
                value = getConcreteValue(state, ref).toString()

            println("\uD83E\uDD7A ${it.key}: $value")
        }
    }


    override fun remove(state: JcState) {
        // TODO: care about Engine.assume -- it's fork, but else state of assume is useless #CM
        check(fixedState == state)
        fixedState = null
        printSpringTestSummary(state)
        selector.remove(state)
        (state.memory as JcConcreteMemory).kill()
        println("removed state: ${state.id}")
        // TODO: generate test? #CM
    }
}
