package machine

import machine.state.JcSpringState
import machine.state.memory.JcSpringMemory
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedField
import org.jacodb.api.jvm.cfg.JcImmediate
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapAddress
import org.usvm.UConcreteHeapRef
import org.usvm.api.makeSymbolicRef
import org.usvm.api.targets.JcTarget
import org.usvm.collection.field.UFieldLValue
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcContext
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcMethodApproximationResolver
import org.usvm.machine.interpreter.JcExprResolver
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.JcState
import org.usvm.targets.UTargetsSet
import utils.handleRefForWrite

class JcSpringInterpreter(
    ctx: JcContext,
    applicationGraph: JcApplicationGraph,
    options: JcMachineOptions,
    jcConcreteMachineOptions: JcConcreteMachineOptions,
    jcSpringMachineOptions: JcSpringMachineOptions,
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
            targets = targets
        )
    }

    private val injectedFields = mutableMapOf<JcField, MutableSet<UConcreteHeapAddress>>()

    private fun injectSymbolicValue(
        scope: JcStepScope,
        field: JcTypedField,
        lValue: UFieldLValue<JcField, UAddressSort>
    ) {
        val jcField = field.field
        val alreadyInjected = injectedFields.getOrPut(jcField) { mutableSetOf() }
        val concreteAddresses = mutableSetOf<UConcreteHeapAddress>()
        val instance = handleRefForWrite(lValue.ref, ctx.trueExpr) { concreteAddresses.add(it) }
        if (instance != null && instance is UConcreteHeapRef)
            concreteAddresses.add(instance.address)

        val toInject = concreteAddresses - alreadyInjected
        if (toInject.isEmpty()) return

        val symbolicValue = scope.makeSymbolicRef(field.type) ?: return
        for (address in toInject) {
            val key = UFieldLValue(lValue.sort, ctx.mkConcreteHeapRef(address), jcField)
            scope.doWithState { memory.write(key, symbolicValue, ctx.trueExpr) }
        }
        alreadyInjected.addAll(toInject)
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
            classInitializerAnalysisAlwaysRequiredForType,
            ::injectSymbolicValue
        )
    }
}
