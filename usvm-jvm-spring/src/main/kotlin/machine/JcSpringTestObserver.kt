package machine

import machine.state.JcSpringState
import org.usvm.statistics.UMachineObserver
import testGeneration.SpringTestInfo
import testGeneration.canGenerateTest
import testGeneration.generateTest

class JcSpringTestObserver : UMachineObserver<JcSpringState> {

    private val tests = mutableListOf<SpringTestInfo>()

    override fun onStateTerminated(state: JcSpringState, stateReachable: Boolean) {
        if (!stateReachable || !state.canGenerateTest()) return
        try {
            tests.add(state.generateTest())
        } catch (e: Throwable) {
            println("generation failed with $e")
        }
    }

    val generatedTests: List<SpringTestInfo> get() = tests
}
 