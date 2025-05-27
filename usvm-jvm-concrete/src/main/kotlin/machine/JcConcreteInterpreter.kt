package machine

import io.ksmt.utils.asExpr
import machine.state.JcConcreteState
import machine.state.concreteMemory.JcConcreteMemory
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.autoboxIfNeeded
import org.jacodb.api.jvm.ext.void
import org.jacodb.impl.features.classpaths.JcUnknownMethod
import org.usvm.UConcreteHeapRef
import org.usvm.api.targets.JcTarget
import org.usvm.jvm.util.toJavaClass
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcConcreteMethodCallInst
import org.usvm.machine.JcContext
import org.usvm.machine.JcDynamicMethodCallInst
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcMethodApproximationResolver
import org.usvm.machine.JcMethodCall
import org.usvm.machine.JcMethodCallBaseInst
import org.usvm.machine.JcVirtualMethodCallInst
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.interpreter.findLambdaCallSite
import org.usvm.machine.interpreter.makeLambdaCallSiteCall
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.newStmt
import org.usvm.machine.state.skipMethodInvocationWithValue
import org.usvm.memory.UMemory
import org.usvm.targets.UTargetsSet
import org.usvm.util.findMethod

open class JcConcreteInterpreter(
    ctx: JcContext,
    applicationGraph: JcApplicationGraph,
    options: JcMachineOptions,
    protected val jcConcreteMachineOptions: JcConcreteMachineOptions,
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
        return JcConcreteState(
            ctx,
            initOwnership,
            method,
            pathConstraints = pathConstraints,
            memory = memory,
            targets = targets
        )
    }

    private fun tryConcreteInvoke(
        scope: JcStepScope,
        stmt: JcMethodCall,
        exprResolver: JcExprResolver
    ): Boolean {
        return scope.calcOnState {
            val memory = memory as JcConcreteMemory
            memory.tryConcreteInvoke(stmt, this, exprResolver, jcConcreteMachineOptions)
        }
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
                            methodResult = JcMethodResult.NoCall
                            newStmt(JcBoxMethodCall(stmt, returnValue, returnType))
                        }
                    }
                }
            }

            is JcReflectionConstructorInvokeResult -> {
                scope.doWithState {
                    skipMethodInvocationWithValue(stmt, stmt.result)
                }
            }

            is JcBoxMethodCall -> {
                scope.doWithState {
                    val result = stmt.resultExpr
                    when (val returnType = stmt.resultType) {
                        is JcPrimitiveType -> {
                            val boxedType = returnType.autoboxIfNeeded() as JcClassType
                            val boxMethod = boxedType.declaredMethods.find {
                                it.name == "valueOf" && it.isStatic && it.parameters.singleOrNull()?.type == returnType
                            }!!
                            methodResult = JcMethodResult.NoCall
                            newStmt(JcConcreteMethodCallInst(stmt.location, boxMethod.method, listOf(result), stmt.returnSite))
                        }

                        else -> skipMethodInvocationWithValue(stmt, result)
                    }
                }
            }

            is JcConcreteMethodCallInst -> {
                if (tryConcreteInvoke(scope, stmt, exprResolver))
                    return

                super.callMethod(scope, stmt, exprResolver)
            }

            is JcVirtualMethodCallInst -> {
                val instance = stmt.arguments[0].asExpr(ctx.addressSort)
                if (instance !is UConcreteHeapRef)
                    return super.callMethod(scope, stmt, exprResolver)

                val callSite = findLambdaCallSite(stmt, scope, instance)
                if (callSite != null) {
                    val lambdaCall = stmt.makeLambdaCallSiteCall(scope, callSite)
                    return callMethod(scope, lambdaCall, exprResolver)
                }

                val type = scope.calcOnState { memory.types.typeOf(instance.address) }
                val typedMethod = type.findMethod(method = stmt.method)
                    ?: return super.callMethod(scope, stmt, exprResolver)

                val method = typedMethod.method
                if (method is JcUnknownMethod)
                    return super.callMethod(scope, stmt, exprResolver)

                val call = stmt.toConcreteMethodCall(method)
                if (tryConcreteInvoke(scope, call, exprResolver))
                    return

                return super.callMethod(scope, stmt, exprResolver)
            }

            is JcDynamicMethodCallInst -> TODO("dynamic call")

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
