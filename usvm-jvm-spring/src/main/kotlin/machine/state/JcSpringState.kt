package machine.state

import machine.state.memory.JcSpringMemory
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

    override fun createNewState(
        ctx: JcContext,
        ownership: MutabilityOwnership,
        entrypoint: JcMethod,
        callStack: UCallStack<JcMethod, JcInst>,
        pathConstraints: UPathConstraints<JcType>,
        memory: UMemory<JcType, JcMethod>,
        models: List<UModelBase<JcType>>,
        pathNode: PathNode<JcInst>,
        forkPoints: PathNode<PathNode<JcInst>>,
        methodResult: JcMethodResult,
        targets: UTargetsSet<JcTarget, JcInst>,
    ): JcState {
        return JcSpringState(
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
        )
    }

    override fun clone(newConstraints: UPathConstraints<JcType>?): JcSpringState {
        return super.clone(newConstraints) as JcSpringState
    }
}
