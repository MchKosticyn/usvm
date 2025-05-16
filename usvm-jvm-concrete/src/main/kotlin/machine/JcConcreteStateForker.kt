package machine

import machine.concreteMemory.JcConcreteMemory
import machine.model.JcConcreteModelEvaluator
import org.jacodb.api.jvm.JcType
import org.usvm.ForkResult
import org.usvm.StateForker
import org.usvm.UAddressSort
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UContext
import org.usvm.UExpr
import org.usvm.UExprTransformer
import org.usvm.UFalse
import org.usvm.UIndexedMethodReturnValue
import org.usvm.UIsSubtypeExpr
import org.usvm.UIsSupertypeExpr
import org.usvm.UNullRef
import org.usvm.URegisterReading
import org.usvm.USort
import org.usvm.UState
import org.usvm.UTrackedSymbol
import org.usvm.UTrue
import org.usvm.collection.array.UAllocatedArrayReading
import org.usvm.collection.array.UInputArrayReading
import org.usvm.collection.array.length.UInputArrayLengthReading
import org.usvm.collection.field.UInputFieldReading
import org.usvm.collection.map.length.UInputMapLengthReading
import org.usvm.collection.map.primitive.UAllocatedMapReading
import org.usvm.collection.map.primitive.UInputMapReading
import org.usvm.collection.map.ref.UAllocatedRefMapWithInputKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithAllocatedKeysReading
import org.usvm.collection.map.ref.UInputRefMapWithInputKeysReading
import org.usvm.collection.set.primitive.UAllocatedSetReading
import org.usvm.collection.set.primitive.UInputSetReading
import org.usvm.collection.set.ref.UAllocatedRefSetWithInputElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithAllocatedElementsReading
import org.usvm.collection.set.ref.UInputRefSetWithInputElementsReading
import org.usvm.machine.JcContext
import org.usvm.machine.USizeSort
import org.usvm.machine.state.JcState
import org.usvm.regions.Region

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

    // TODO: use this
    @Suppress("unused")
    private class ExprVisitor(
        ctx: JcContext
    ): UExprTransformer<JcType, USizeSort>(ctx) {
        override fun <Sort : USort> transform(expr: URegisterReading<Sort>): UExpr<Sort> {
            TODO("Not yet implemented")
        }

        override fun <Field, Sort : USort> transform(expr: UInputFieldReading<Field, Sort>): UExpr<Sort> {
            TODO("Not yet implemented")
        }

        override fun <Method, Sort : USort> transform(expr: UIndexedMethodReturnValue<Method, Sort>): UExpr<Sort> {
            TODO("Not yet implemented")
        }

        override fun <Sort : USort> transform(expr: UTrackedSymbol<Sort>): UExpr<Sort> {
            TODO("Not yet implemented")
        }

        override fun transform(expr: UConcreteHeapRef): UExpr<UAddressSort> {
            return expr
        }

        override fun transform(expr: UNullRef): UExpr<UAddressSort> {
            return expr
        }

        override fun transform(expr: UIsSupertypeExpr<JcType>): UBoolExpr {
            TODO("Not yet implemented")
        }

        override fun transform(expr: UIsSubtypeExpr<JcType>): UBoolExpr {
            TODO("Not yet implemented")
        }

        override fun transform(expr: UInputRefSetWithInputElementsReading<JcType>): UBoolExpr {
            TODO("Not yet implemented")
        }

        override fun transform(expr: UInputRefSetWithAllocatedElementsReading<JcType>): UBoolExpr {
            TODO("Not yet implemented")
        }

        override fun transform(expr: UAllocatedRefSetWithInputElementsReading<JcType>): UBoolExpr {
            TODO("Not yet implemented")
        }

        override fun <ElemSort : USort, Reg : Region<Reg>> transform(expr: UInputSetReading<JcType, ElemSort, Reg>): UBoolExpr {
            TODO("Not yet implemented")
        }

        override fun <ElemSort : USort, Reg : Region<Reg>> transform(expr: UAllocatedSetReading<JcType, ElemSort, Reg>): UBoolExpr {
            TODO("Not yet implemented")
        }

        override fun transform(expr: UInputMapLengthReading<JcType, USizeSort>): UExpr<USizeSort> {
            TODO("Not yet implemented")
        }

        override fun <Sort : USort> transform(expr: UInputRefMapWithInputKeysReading<JcType, Sort>): UExpr<Sort> {
            TODO("Not yet implemented")
        }

        override fun <Sort : USort> transform(expr: UInputRefMapWithAllocatedKeysReading<JcType, Sort>): UExpr<Sort> {
            TODO("Not yet implemented")
        }

        override fun <Sort : USort> transform(expr: UAllocatedRefMapWithInputKeysReading<JcType, Sort>): UExpr<Sort> {
            TODO("Not yet implemented")
        }

        override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(expr: UInputMapReading<JcType, KeySort, Sort, Reg>): UExpr<Sort> {
            TODO("Not yet implemented")
        }

        override fun <KeySort : USort, Sort : USort, Reg : Region<Reg>> transform(expr: UAllocatedMapReading<JcType, KeySort, Sort, Reg>): UExpr<Sort> {
            TODO("Not yet implemented")
        }

        override fun transform(expr: UInputArrayLengthReading<JcType, USizeSort>): UExpr<USizeSort> {
            TODO("Not yet implemented")
        }

        override fun <Sort : USort> transform(expr: UInputArrayReading<JcType, Sort, USizeSort>): UExpr<Sort> {
            TODO("Not yet implemented")
        }

        override fun <Sort : USort> transform(expr: UAllocatedArrayReading<JcType, Sort, USizeSort>): UExpr<Sort> {
            TODO("Not yet implemented")
        }
    }

//    private fun tryModifyModel(
//        ctx: JcContext,
//        solver: USolverBase<JcType>,
//        freeCondition: UBoolExpr,
//        basePathConstraints: UPathConstraints<JcType>,
//        baseModel: UModelBase<JcType>,
//    ) {
//        val clonedPs = basePathConstraints.clone()
//        clonedPs += freeCondition
//        baseModel.regions
//        solver.
//    }

//    @Suppress("UNCHECKED_CAST")
    override fun <T : UState<Type, *, *, Context, *, T>, Type, Context : UContext<*>> forkMulti(
        state: T,
        conditions: Iterable<UBoolExpr>
    ): List<T?> {
        val memory = state.memory as JcConcreteMemory
        if (!memory.concretization)
            return baseStateForker.forkMulti(state, conditions)

        val model = memory.getFixedModel(state as JcState)
        val results = mutableListOf<T?>()
        val freeConditions = mutableMapOf<Int, UBoolExpr>()
        var conditionFound = false
        var i = 0
        for (condition in conditions) {
            if (conditionFound) {
                results.add(null)
                continue
            }

//            JcConcreteModelEvaluator.shouldComplete = false
            val evaledCondition = model.eval(condition)
//            JcConcreteModelEvaluator.shouldComplete = true
            when (evaledCondition) {
                is UTrue -> {
                    results.add(state)
                    conditionFound = true
                }
                is UFalse -> {
                    results.add(null)
                }
                else -> {
                    freeConditions[i] = evaledCondition
                    results.add(null)
                }
            }
            i++
        }

        if (conditionFound)
            return results

        // TODO: #hack
        results[0] = state
        return results
//        val solver = state.ctx.solver<Type>()
//        for ((idx, freeCondition) in freeConditions) {
//
//        }
//
//        var newModel: UModelBase<Type>? = null
//        for (condition in conditions) {
//            val currentPs = (state as T).pathConstraints.clone()
//            currentPs += condition
//            when (val result = solver.check(currentPs)) {
//                is USatResult<UModelBase<Type>> -> {
//                    newModel = result.model
//                    break
//                }
//                else -> Unit
//            }
//        }
//
//        if (newModel == null) {
//            println("[WARNING] JcConcreteStateForker.forkMulti: no sat result")
//            return results
//        }
//
//        memory.backtrackConcretization(newModel as UModelBase<JcType>)
//        return results
    }
}
