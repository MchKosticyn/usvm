package machine

import machine.state.JcSpringState
import org.jacodb.api.jvm.JcMethod
import org.usvm.machine.state.JcState
import org.usvm.statistics.TimeStatistics
import kotlin.time.Duration

class JcSpringTimeStatistics: TimeStatistics<JcMethod, JcState>() {
    private val pathTimes = mutableMapOf<String, Duration>()

    fun getTimeSpentOnPath(path: String) = pathTimes.getOrDefault(path, Duration.ZERO)

    override fun onState(parent: JcState, forks: Sequence<JcState>) {
        val parentSpringState = parent as JcSpringState
        val path = parentSpringState.path ?: return
        pathTimes.merge(path, methodStopwatch.elapsed) { current, elapsed -> current + elapsed }
    }
}
