package machine.state

import machine.state.memory.JcSpringMemory
import machine.state.pinnedValues.JcPinnedKey
import machine.state.pinnedValues.JcSpringPinnedValue
import machine.state.pinnedValues.JcSpringPinnedValueSource
import machine.state.pinnedValues.JcSpringPinnedValues
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.targets.JcTarget
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcContext
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.memory.UMemory
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet

class JcSpringState(
    ctx: JcContext,
    ownership: MutabilityOwnership,
    entrypoint: JcMethod,
    callStack: UCallStack<JcMethod, JcInst> = UCallStack(),
    pathConstraints: UPathConstraints<JcType> = UPathConstraints(ctx, ownership),
    memory: UMemory<JcType, JcMethod> = JcSpringMemory(ctx, ownership, pathConstraints.typeConstraints),
    models: List<UModelBase<JcType>> = listOf(),
    pathNode: PathNode<JcInst> = PathNode.root(),
    forkPoints: PathNode<PathNode<JcInst>> = PathNode.root(),
    methodResult: JcMethodResult = JcMethodResult.NoCall,
    targets: UTargetsSet<JcTarget, JcInst> = UTargetsSet.empty(),
    val pinnedValues: JcSpringPinnedValues = JcSpringPinnedValues(),
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

    internal val springMemory: JcSpringMemory
        get() = this.memory as JcSpringMemory

    private fun firstPinnedOfSourceOrNull(source: JcSpringPinnedValueSource): UExpr<out USort>? {
        return pinnedValues.getValuesOfSource<JcPinnedKey>(source).values.firstOrNull()?.getExpr()
    }
    
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

    fun getPinnedValue(key: JcPinnedKey): JcSpringPinnedValue? {
        return pinnedValues.getValue(key)
    }

    fun setPinnedValue(key: JcPinnedKey, value: UExpr<out USort>, type: JcType) {
        return pinnedValues.setValue(key, JcSpringPinnedValue(value, type))
    }

    fun createPinnedIfAbsent(
        key: JcPinnedKey,
        type: JcType,
        scope: JcStepScope,
        sort: USort,
        nullable: Boolean = true
    ): JcSpringPinnedValue? {
        return pinnedValues.createIfAbsent(key, type, scope, sort, nullable)
    }

    fun createPinnedAndReplace(
        key: JcPinnedKey, 
        type: JcType, 
        scope: JcStepScope, 
        sort: USort, 
        nullable: Boolean = true
    ): JcSpringPinnedValue? {
        return pinnedValues.createAndReplace(key, type, scope, sort, nullable)
    }

    fun getPinnedValueKey(expr: UExpr<out USort>): JcPinnedKey? {
        return pinnedValues.getKeyOfExpr(expr)
    }

    fun hasEnoughInfoForTest(): Boolean {
        return pinnedValues.getValue(JcPinnedKey.requestPath()) != null
    }

    override fun clone(newConstraints: UPathConstraints<JcType>?): JcSpringState {
        println("\u001B[34m" + "Forked on method ${callStack.lastMethod()}" + "\u001B[0m")
        return super.clone(newConstraints) as JcSpringState
    }
}
