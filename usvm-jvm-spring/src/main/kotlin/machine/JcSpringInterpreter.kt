package machine

import machine.state.JcSpringState
import machine.concreteMemory.JcConcreteMemory
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.api.targets.JcTarget
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UPathConstraints
import org.usvm.machine.JcApplicationGraph
import org.usvm.machine.JcContext
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachineOptions
import org.usvm.targets.UTargetsSet

class JcSpringInterpreter(
    ctx: JcContext,
    applicationGraph: JcApplicationGraph,
    options: JcMachineOptions,
    observer: JcInterpreterObserver? = null,
): JcConcreteInterpreter(ctx, applicationGraph, options, observer) {

    override fun createState(
        initOwnership: MutabilityOwnership,
        method: JcMethod,
        targets: UTargetsSet<JcTarget, JcInst>
    ): JcSpringState {
        val pathConstraints = UPathConstraints<JcType>(ctx, initOwnership)
        val memory = JcConcreteMemory(ctx, initOwnership, pathConstraints.typeConstraints)
        return JcSpringState(
            ctx,
            initOwnership,
            method,
            pathConstraints = pathConstraints,
            memory = memory,
            targets = targets
        )
    }
}
