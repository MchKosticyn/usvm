package machine.ps.weighters

import machine.state.JcSpringState
import org.usvm.machine.state.JcState
import org.usvm.ps.StateWeighter
import org.usvm.ps.weighters.weightersLog

class JcSpringEdgeCaseWeighter: StateWeighter<JcState, Int> {

    private companion object {
        private const val GOOD_WEIGHT = 10
        private const val BAD_WEIGHT = 0
    }

    override fun weight(state: JcState): Int {
        state as JcSpringState
        // TODO: check validation errors
        val result = if (state.isExceptional) GOOD_WEIGHT else BAD_WEIGHT
        weightersLog.println("JcSpringEdgeCaseWeighter: state = ${state.id}, weight = $result")
        return result
    }
}
