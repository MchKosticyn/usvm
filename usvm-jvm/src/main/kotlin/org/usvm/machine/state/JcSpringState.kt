package org.usvm.machine.state

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.spring.JcSpringTest
import org.usvm.api.targets.JcTarget
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcContext
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.concreteMemory.JcConcreteMemory
import org.usvm.machine.state.pinnedValues.JcSpringPinnedValue
import org.usvm.machine.state.pinnedValues.JcSpringPinnedValueKey
import org.usvm.machine.state.pinnedValues.JcSpringPinnedValueSource
import org.usvm.machine.state.pinnedValues.JcSpringPinnedValues
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet

class JcSpringState(
    ctx: JcContext,
    ownership: MutabilityOwnership,
    entrypoint: JcMethod,
    callStack: UCallStack<JcMethod, JcInst> = UCallStack(),
    pathConstraints: UPathConstraints<JcType> = UPathConstraints(ctx, ownership),
    memory: UMemory<JcType, JcMethod> = JcConcreteMemory(ctx, ownership, pathConstraints.typeConstraints),
    models: List<UModelBase<JcType>> = listOf(),
    pathNode: PathNode<JcInst> = PathNode.root(),
    forkPoints: PathNode<PathNode<JcInst>> = PathNode.root(),
    methodResult: JcMethodResult = JcMethodResult.NoCall,
    targets: UTargetsSet<JcTarget, JcInst> = UTargetsSet.empty(),
    var pinnedValues: JcSpringPinnedValues = JcSpringPinnedValues(),
    var resultConclusion: JcSpringTest? = null,
) : JcState(
    ctx,
    ownership,
    entrypoint,
    callStack,
    pathConstraints,
    memory,
    models,
    pathNode,
    forkPoints,
    methodResult,
    targets
) {
    private fun firstPinnedOfSourceOrNull(source: JcSpringPinnedValueSource): UExpr<out USort>? {
        return pinnedValues.getValuesOfSource(source).values.firstOrNull()?.getExpr()
    }

    val response get() = firstPinnedOfSourceOrNull(JcSpringPinnedValueSource.RESPONSE)
    val requestMethod get() = firstPinnedOfSourceOrNull(JcSpringPinnedValueSource.REQUEST_METHOD)
    val requestPath get() = firstPinnedOfSourceOrNull(JcSpringPinnedValueSource.REQUEST_PATH)

    companion object {
        fun defaultFromJcState(state: JcState): JcSpringState = JcSpringState(
            state.ctx,
            state.ownership,
            state.entrypoint,
            state.callStack,
            state.pathConstraints,
            state.memory,
            state.models,
            state.pathNode,
            state.forkPoints,
            state.methodResult,
            state.targets,
        )
    }

    fun getPinnedValue(key: JcSpringPinnedValueKey): JcSpringPinnedValue? {
        return pinnedValues.getValue(key)
    }

    fun setPinnedValue(key: JcSpringPinnedValueKey, value: UExpr<out USort>, type: JcType) {
        return pinnedValues.setValue(key, JcSpringPinnedValue(value, type))
    }

    fun createPinnedIfAbsent(key: JcSpringPinnedValueKey, type: JcType, scope: JcStepScope, sort: USort, nullable: Boolean = true): JcSpringPinnedValue? {
        return pinnedValues.createIfAbsent(key, type, scope, sort, nullable)
    }

    fun getPinnedValueKey(expr: UExpr<out USort>): JcSpringPinnedValueKey? {
        return pinnedValues.getKeyOfExpr(expr)
    }

    fun getPinnedValuesOfSource(source: JcSpringPinnedValueSource) : Map<JcSpringPinnedValueKey, JcSpringPinnedValue> {
        return pinnedValues.getValuesOfSource(source)
    }

    fun hasEnoughInfoForTest(): Boolean {
        return pinnedValues.getValue(JcSpringPinnedValueKey.requestPath()) != null
    }

    fun getResult(): UExpr<out USort>? {
        return pinnedValues.getValue(JcSpringPinnedValueKey.response())?.getExpr()
    }

    override fun clone(newConstraints: UPathConstraints<JcType>?): JcSpringState {
        val newThisOwnership = MutabilityOwnership()
        val cloneOwnership = MutabilityOwnership()
        val clonedConstraints = newConstraints?.also {
            this.pathConstraints.changeOwnership(newThisOwnership)
            it.changeOwnership(cloneOwnership)
        } ?: pathConstraints.clone(newThisOwnership, cloneOwnership)
        this.ownership = newThisOwnership
        val new = JcSpringState(
            ctx,
            cloneOwnership,
            entrypoint,
            callStack.clone(),
            clonedConstraints,
            memory.clone(clonedConstraints.typeConstraints, newThisOwnership, cloneOwnership),
            models,
            pathNode,
            forkPoints,
            methodResult,
            targets.clone(),
        )
        new.pinnedValues = pinnedValues
        new.resultConclusion =
            resultConclusion?.let { error("State cannot be cloned if resultConclusion was generated from it") }
        return new
    }
}