package machine

import machine.state.JcSpringState
import org.usvm.jvm.rendering.testRenderer.JcTestInfo
import org.usvm.statistics.UMachineObserver
import org.usvm.test.api.UTest
import testGeneration.canGenerateTest
import testGeneration.generateTest
import testGeneration.generateTestInfo


interface TestReproducer {
    fun reproduce(test: UTest): Boolean
    fun kill()
}

interface TestRenderer {
    fun render(test: UTest, info: JcTestInfo)
}

class JcSpringTestObserver(
    private val testReproducer: TestReproducer,
    private val testRenderer: TestRenderer
) : UMachineObserver<JcSpringState> {

    override fun onStateTerminated(state: JcSpringState, stateReachable: Boolean) {
        state.callStack.push(state.entrypoint, state.entrypoint.instList[0])
        if (!stateReachable || !state.hasEnoughInfoForTest()) return
        try {
            if (!state.canGenerateTest()) return
            testRenderer.render(state.generateTest(), state.generateTestInfo())
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
