package machine

import machine.state.JcSpringState
import machine.state.memory.JcSpringMemory
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcImmediate
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.UConcreteHeapRef
import org.usvm.api.targets.JcTarget
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.jvm.util.toJcType
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcContext
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcMethodApproximationResolver
import org.usvm.machine.JcMethodCallBaseInst
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.skipMethodInvocationWithValue
import org.usvm.targets.UTargetsSet

class JcSpringInterpreter(
    ctx: JcContext,
    applicationGraph: JcApplicationGraph,
    options: JcMachineOptions,
    jcConcreteMachineOptions: JcConcreteMachineOptions,
    protected val jcSpringMachineOptions: JcSpringMachineOptions,
    observer: JcInterpreterObserver? = null,
): JcConcreteInterpreter(ctx, applicationGraph, options, jcConcreteMachineOptions, observer) {

    override val approximationResolver: JcMethodApproximationResolver =
        JcSpringMethodApproximationResolver(ctx, applicationGraph, jcConcreteMachineOptions, jcSpringMachineOptions)

    override fun createState(
        initOwnership: MutabilityOwnership,
        method: JcMethod,
        targets: UTargetsSet<JcTarget, JcInst>
    ): JcSpringState {
        val pathConstraints = UPathConstraints<JcType>(ctx, initOwnership)
        val memory = JcSpringMemory(ctx, initOwnership, pathConstraints.typeConstraints)
        return JcSpringState(
            ctx,
            initOwnership,
            method,
            pathConstraints = pathConstraints,
            memory = memory,
            targets = targets,
            springAnalysisMode = jcSpringMachineOptions.springAnalysisMode
        )
    }

    override fun createExprResolver(
        ctx: JcContext,
        scope: JcStepScope,
        options: JcMachineOptions,
        localToIdx: (JcMethod, JcImmediate) -> Int,
        mkTypeRef: (JcState, JcType) -> Pair<UConcreteHeapRef, Boolean>,
        mkStringConstRef: (JcState, String, Boolean) -> Pair<UConcreteHeapRef, Boolean>,
        classInitializerAnalysisAlwaysRequiredForType: (JcRefType) -> Boolean,
    ): JcExprResolver {
        return JcSpringExprResolver(
            ctx,
            scope,
            options,
            localToIdx,
            mkTypeRef,
            mkStringConstRef,
            classInitializerAnalysisAlwaysRequiredForType
        )
    }

    override fun callMethod(
        scope: JcStepScope,
        stmt: JcMethodCallBaseInst,
        exprResolver: JcExprResolver
    ) {
        when (stmt) {
            is JcMockMethodInvokeResult -> {
                scope.doWithState {
                    this as JcSpringState
                    // TODO: Mock exception throws too #AA
                    if (methodResult is JcMethodResult.Success) {
                        val methodResult = methodResult as JcMethodResult.Success
                        val returnType = methodResult.method.returnType.toJcType(ctx.cp)
                        addMock(stmt.method, methodResult.value, returnType!!)
                        skipMethodInvocationWithValue(stmt, methodResult.value)
                    }
                }
            }

            else -> super.callMethod(scope, stmt, exprResolver)
        }
    }
}
