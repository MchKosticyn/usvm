package machine

import machine.state.concreteMemory.JcConcreteMemory
import org.jacodb.api.jvm.JcType
import org.usvm.ForkResult
import org.usvm.StateForker
import org.usvm.UBoolExpr
import org.usvm.UContext
import org.usvm.UFalse
import org.usvm.UState
import org.usvm.UTrue
import org.usvm.machine.state.JcState
import org.usvm.model.UModelBase
import org.usvm.solver.USatResult

class JcConcreteStateForker(
    private val baseStateForker: StateForker,
) : StateForker {
    override fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext<*>> fork(
        state: T,
        condition: UBoolExpr
    ): ForkResult<T> {
        val memory = state.memory as JcConcreteMemory
        if (!memory.concretization)
            return baseStateForker.fork(state, condition)

        val model = memory.getFixedModel(state as JcState)
        return when (val evaledCondition = model.eval(condition)) {
            is UTrue -> ForkResult(positiveState = state, negativeState = null)
            is UFalse -> ForkResult(positiveState = null, negativeState = state)
            else -> error("JcConcreteStateForker.fork: Unknown condition in model: $evaledCondition")
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext<*>> forkMulti(
        state: T,
        conditions: Iterable<UBoolExpr>
    ): List<T?> {
        val memory = state.memory as JcConcreteMemory
        if (!memory.concretization)
            return baseStateForker.forkMulti(state, conditions)

        val model = memory.getFixedModel(state as JcState)
        val results = mutableListOf<T?>()
        var conditionFound = false
        for (condition in conditions) {
            if (conditionFound) {
                results.add(null)
                continue
            }

            when (model.eval(condition)) {
                is UTrue -> {
                    results.add(state)
                    conditionFound = true
                }
                is UFalse -> {
                    results.add(null)
                }
                else -> {
                    results.add(null)
                }
            }
        }

        if (conditionFound)
            return results

        val solver = state.ctx.solver<Type>()
        for ((idx, condition) in conditions.withIndex()) {
            val currentPs = (state as T).pathConstraints.clone()
            for (constraint in memory.concretizationConstraints)
                currentPs += constraint
            currentPs += condition
            when (val result = solver.check(currentPs)) {
                is USatResult<UModelBase<Type>> -> {
                    memory.setFixedModel(state, result.model as UModelBase<JcType>)
                    results[idx] = state
                    return results
                }
                else -> Unit
            }
        }

        var newModel: UModelBase<Type>? = null
        for (condition in conditions) {
            val currentPs = (state as T).pathConstraints.clone()
            currentPs += condition
            when (val result = solver.check(currentPs)) {
                is USatResult<UModelBase<Type>> -> {
                    newModel = result.model
                    break
                }
                else -> Unit
            }
        }

        if (newModel == null) {
            println("[WARNING] JcConcreteStateForker.forkMulti: no sat result")
            return results
        }

        memory.backtrackConcretization(newModel as UModelBase<JcType>)
        return results
    }
}
