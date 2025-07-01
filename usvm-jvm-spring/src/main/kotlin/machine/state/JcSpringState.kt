package machine.state

import machine.HandlerMethodData
import machine.JcSpringAnalysisMode
import machine.state.memory.JcSpringMemory
import machine.state.pinnedValues.JcPinnedKey
import machine.state.pinnedValues.JcPinnedValue
import machine.state.pinnedValues.JcSpringMockedCalls
import machine.state.pinnedValues.JcSpringPinnedValues
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.void
import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.USort
import org.usvm.api.targets.JcTarget
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcContext
import org.usvm.machine.interpreter.JcStepScope
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet

typealias tableContent = Pair<List<Pair<UHeapRef, Int>>, JcClassType>

class JcSpringState(
    ctx: JcContext,
    ownership: MutabilityOwnership,
    entrypoint: JcMethod,
    callStack: UCallStack<JcMethod, JcInst> = UCallStack(),
    pathConstraints: UPathConstraints<JcType> = UPathConstraints(ctx, ownership),
    memory: JcSpringMemory = JcSpringMemory(ctx, ownership, pathConstraints.typeConstraints),
    models: List<UModelBase<JcType>> = listOf(),
    pathNode: PathNode<JcInst> = PathNode.root(),
    forkPoints: PathNode<PathNode<JcInst>> = PathNode.root(),
    methodResult: JcMethodResult = JcMethodResult.NoCall,
    targets: UTargetsSet<JcTarget, JcInst> = UTargetsSet.empty(),
    internal val springAnalysisMode: JcSpringAnalysisMode,
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
    var pinnedValues: JcSpringPinnedValues = JcSpringPinnedValues()
    var mockedMethodCalls: JcSpringMockedCalls = JcSpringMockedCalls()

    var handlerData: List<HandlerMethodData> = listOf()
    var roleStrings: Set<UExpr<out USort>> = setOf()

    var tableEntities = emptyMap<String, tableContent>()

    internal val springMemory: JcSpringMemory
        get() = this.memory as JcSpringMemory

    fun addTableEntity(tableName: String, entity: UHeapRef, type: JcClassType, idx: Int) {
        val (entities, currentType) = tableEntities.getOrDefault(tableName, emptyList<Pair<UHeapRef, Int>>() to type)
        check(currentType == type)
        val updatedEntities = (entities + listOf(entity to idx)).distinct() to currentType
        tableEntities += tableName to updatedEntities
    }

    fun addMock(method: JcMethod, result: UExpr<out USort>, type: JcType) {
        check(method.returnType.typeName != ctx.cp.void.typeName) { "Cannot mock void methods" }
        mockedMethodCalls.addMock(method, JcPinnedValue(result, type))
    }

    fun getPinnedValue(key: JcPinnedKey): JcPinnedValue? {
        return pinnedValues.getValue(key)
    }

    fun setPinnedValue(key: JcPinnedKey, value: UExpr<out USort>, type: JcType) {
        return pinnedValues.setValue(key, JcPinnedValue(value, type))
    }

    fun removePinnedValue(key: JcPinnedKey) {
        return pinnedValues.removeValue(key)
    }

    fun createPinnedIfAbsent(
        key: JcPinnedKey,
        type: JcType,
        scope: JcStepScope,
        sort: USort,
        nullable: Boolean = true
    ): JcPinnedValue? {
        return pinnedValues.createIfAbsent(key, type, scope, sort, nullable)
    }

    fun createPinnedAndReplace(
        key: JcPinnedKey,
        type: JcType,
        scope: JcStepScope,
        sort: USort,
        nullable: Boolean = true
    ): JcPinnedValue? {
        return pinnedValues.createAndPut(key, type, scope, sort, nullable)
    }

    internal val path: String? get() {
        val pathValue = getPinnedValue(JcPinnedKey.requestPath()) ?: return null
        val pathExpr = pathValue.getExpr() as? UConcreteHeapRef
            ?: error("Unexpected symbolic path")
        val pathString = springMemory.tryHeapRefToObject(pathExpr) as? String
            ?: error("Unexpected symbolic path")
        return pathString
    }

    override fun clone(newConstraints: UPathConstraints<JcType>?): JcSpringState {
        println("\u001B[34m" + "Forked on method ${callStack.lastMethod()}" + "\u001B[0m")
        val cloned = super.clone(newConstraints) as JcSpringState
        cloned.pinnedValues = pinnedValues.copy()
        cloned.mockedMethodCalls = mockedMethodCalls.copy()
        println("\u001B[34m[${id}] -> [${id}, ${cloned.id}]\u001B[0m")
        return cloned
    }
}
