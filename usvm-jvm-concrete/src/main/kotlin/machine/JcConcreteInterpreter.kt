package machine

import machine.concreteMemory.JcConcreteMemory
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.autoboxIfNeeded
import org.jacodb.api.jvm.ext.void
import org.usvm.UConcreteHeapRef
import org.usvm.api.targets.JcTarget
import org.usvm.api.util.Reflection.toJavaClass
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcConcreteMethodCallInst
import org.usvm.machine.JcContext
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcMethodApproximationResolver
import org.usvm.machine.JcMethodCallBaseInst
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.skipMethodInvocationWithValue
import org.usvm.memory.UMemory
import org.usvm.targets.UTargetsSet

open class JcConcreteInterpreter(
    ctx: JcContext,
    applicationGraph: JcApplicationGraph,
    options: JcMachineOptions,
    private val jcConcreteMachineOptions: JcConcreteMachineOptions,
    observer: JcInterpreterObserver? = null,
): JcInterpreter(ctx, applicationGraph, options, observer) {

    override val approximationResolver: JcMethodApproximationResolver =
        JcConcreteMethodApproximationResolver(ctx, applicationGraph)

    override fun createState(
        initOwnership: MutabilityOwnership,
        method: JcMethod,
        targets: UTargetsSet<JcTarget, JcInst>
    ): JcState {
        val pathConstraints = UPathConstraints<JcType>(ctx, initOwnership)
        val memory = JcConcreteMemory(ctx, initOwnership, pathConstraints.typeConstraints)
        return JcState(
            ctx,
            initOwnership,
            method,
            pathConstraints = pathConstraints,
            memory = memory,
            targets = targets
        )
    }

    override fun callMethod(
        scope: JcStepScope,
        stmt: JcMethodCallBaseInst,
        exprResolver: JcExprResolver
    ) {
        when (stmt) {
            is JcConcreteInvocationResult -> {
                scope.calcOnState { skipMethodInvocationWithValue(stmt, stmt.returnExpr) }
            }

            is JcReflectionInvokeResult -> {
                scope.doWithState {
                    when (val returnType = stmt.invokeMethod.returnType) {
                        ctx.cp.void -> skipMethodInvocationWithValue(stmt, ctx.nullRef)
                        else -> {
                            val returnValue = (methodResult as JcMethodResult.Success).value
                            newStmt(JcBoxMethodCall(stmt, returnValue, returnType))
                        }
                    }
                }
            }

            is JcBoxMethodCall -> {
                scope.doWithState {
                    val result = stmt.resultExpr
                    when (val returnType = stmt.resultType) {
                        is JcPrimitiveType -> {
                            val boxedType = returnType.autoboxIfNeeded() as JcClassType
                            val boxMethod = boxedType.declaredMethods.find {
                                it.name == "valueOf" && it.isStatic && it.parameters.singleOrNull() == returnType
                            }!!
                            newStmt(JcConcreteMethodCallInst(stmt.location, boxMethod.method, listOf(result), stmt.returnSite))
                        }

                        else -> skipMethodInvocationWithValue(stmt, result)
                    }
                }
            }

            is JcConcreteMethodCallInst -> {
                val success = scope.calcOnState {
                    val memory = memory as JcConcreteMemory
                    memory.tryConcreteInvoke(stmt, this, exprResolver, jcConcreteMachineOptions)
                }

                if (success)
                    return

                super.callMethod(scope, stmt, exprResolver)
            }

            else -> super.callMethod(scope, stmt, exprResolver)
        }
    }

    override fun allocateString(memory: UMemory<JcType, JcMethod>, value: String): Pair<UConcreteHeapRef, Boolean> {
        memory as JcConcreteMemory
        // Tries to allocate string in concrete memory
        val address = memory.tryAllocateConcrete(value, ctx.stringType)
        if (address != null)
            return address to true

        return super.allocateString(memory, value)
    }

    override fun allocateTypeInstance(memory: UMemory<JcType, JcMethod>, type: JcType): Pair<UConcreteHeapRef, Boolean> {
        memory as JcConcreteMemory
        // Tries to allocate class in concrete memory
        val address = memory.tryAllocateConcrete(type.toJavaClass(JcConcreteMemoryClassLoader), ctx.classType)
        if (address != null)
            return address to true

        return super.allocateTypeInstance(memory, type)
    }
}
