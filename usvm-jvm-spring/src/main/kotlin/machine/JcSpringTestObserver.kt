package machine

import machine.state.JcSpringState
import org.usvm.statistics.UMachineObserver
import org.usvm.test.api.UTest
import testGeneration.canGenerateTest
import testGeneration.generateTest


interface TestReproducer {
    fun reproduce(test: UTest): Boolean
    fun kill()
}

class JcSpringTestObserver(
    private val testReproducer: TestReproducer
) : UMachineObserver<JcSpringState> {

    override fun onStateTerminated(state: JcSpringState, stateReachable: Boolean) {
        state.callStack.push(state.entrypoint, state.entrypoint.instList[0])
        if (!stateReachable || !state.hasEnoughInfoForTest()) return
        try {
            if (!state.canGenerateTest()) return
            val success = testReproducer.reproduce(state.generateTest())
            println("Test success: $success")
        } catch (e: Throwable) {
            println("generation failed with $e")
        }
    }

    override fun onMachineStopped() {
       testReproducer.kill()
    }
}
