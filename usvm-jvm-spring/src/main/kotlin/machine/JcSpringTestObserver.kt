package machine

import machine.state.JcSpringState
import org.jacodb.api.jvm.JcClasspath
import org.usvm.statistics.UMachineObserver
import org.usvm.test.api.spring.JcSpringTest
import org.usvm.test.api.UTest


interface TestReproducer {
    fun reproduce(test: UTest): Boolean
}

class JcSpringTestObserver(
    private val machine: JcSpringMachine,
    cp: JcClasspath,
    private val testReproducer: TestReproducer
) : UMachineObserver<JcSpringState> {

//    init {
//        // TODO: Find better way to get deps without unprivating jcMachineOptions #AA
//        val options = machine.jcMachineOptions
//        check(options.projectLocations != null && options.dependenciesLocations != null)
//        val reproducingLocations = System.getenv("usvm.jvm.testReproducingDeps.paths").split(";")
//        val locations = (options.projectLocations + options.dependenciesLocations).map { it.path } + reproducingLocations
//        val opt = UTestExecutionOptions(execMode= InstrumentedProcess.UTestExecMode.RESULT_ONLY)
//        exec = UTestConcreteExecutor(
//            instrumentationClassFactory = NoInstrumentationFactory::class,
//            testingProjectClasspath = locations.joinToString(File.pathSeparator),
//            jcClasspath = cp,
//            timeout = Duration.INFINITE,
//            opts = opt
//        )
//        runBlocking { exec.ensureRunnerAlive() }
//    }

    override fun onStateTerminated(state: JcSpringState, stateReachable: Boolean) {
        state.callStack.push(state.entrypoint, state.entrypoint.instList[0])
        if (!stateReachable || !state.hasEnoughInfoForTest()) return
        try {
            val test = JcSpringTest.generateFromState(state)
            val testDsl = test.generateTestDSL()

            // TODO: testIsValidAndMayBeRendered

            val res = exec.executeSync(testDsl)
            println(res)

            JcSpringTestRenderManager().render(
                state.entrypoint.enclosingClass.classpath,
                listOf(
                    UTestRenderWrapper(
                        testDsl,
                        JcSpringTestMeta(test.generatedTestClass, test.getPath(), JcSpringTestKind.WebMVC)
                    )
                )
            )
            machine.testPool.add(test)
        } catch (e: Throwable) {
            println("generation failed with $e")
        }
    }

    override fun onMachineStopped() {
        machine.testPool.removeIf(::testIsValidAndMayBeRendered)
    }

    // TODO: reproduction
    private fun testIsValidAndMayBeRendered(test: JcSpringTest): Boolean = true
}
