package machine.state

import machine.state.concreteMemory.JcConcreteMemory
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.PathNode
import org.usvm.UCallStack
import org.usvm.api.targets.JcTarget
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcContext
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.model.UModelBase
import org.usvm.targets.UTargetsSet

open class JcConcreteState(
    ctx: JcContext,
    ownership: MutabilityOwnership,
    entrypoint: JcMethod,
    callStack: UCallStack<JcMethod, JcInst> = UCallStack(),
    pathConstraints: UPathConstraints<JcType> = UPathConstraints(ctx, ownership),
    memory: JcConcreteMemory,
    models: List<UModelBase<JcType>> = listOf(),
    pathNode: PathNode<JcInst> = PathNode.root(),
    forkPoints: PathNode<PathNode<JcInst>> = PathNode.root(),
    methodResult: JcMethodResult = JcMethodResult.NoCall,
    targets: UTargetsSet<JcTarget, JcInst> = UTargetsSet.empty()
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
    internal val concreteMemory: JcConcreteMemory
        get() = this.memory as JcConcreteMemory

    override fun clone(newConstraints: UPathConstraints<JcType>?): JcConcreteState {
        return super.clone() as JcConcreteState
    }
}
