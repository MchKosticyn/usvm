package machine

import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.machine.JcLoopTracker
import org.usvm.machine.state.JcState
import org.usvm.ps.StateLoopTracker

internal class JcSpringMachineLoopTracker(
    private val jcTracker: JcLoopTracker
) : StateLoopTracker<JcLoopTracker.LoopInfo, JcInst, JcState> {

    override fun findLoopEntrance(statement: JcInst): JcLoopTracker.LoopInfo? {
        return jcTracker.findLoopEntrance(statement)
    }

    override fun isLoopIterationFork(loop: JcLoopTracker.LoopInfo, forkPoint: JcInst): Boolean {
        val loopMethod = loop.loop.head.method
        val name = loopMethod.name
        val className = loopMethod.enclosingClass.name
        if (name == "perform" && className.contains("SpringMvcPerformer")) {
            return false
        }

        return jcTracker.isLoopIterationFork(loop, forkPoint)
    }
}
