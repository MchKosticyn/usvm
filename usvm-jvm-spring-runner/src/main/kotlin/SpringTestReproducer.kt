import kotlinx.coroutines.runBlocking
import machine.JcConcreteMachineOptions
import org.jacodb.api.jvm.JcClasspath
import org.usvm.instrumentation.executor.UTestConcreteExecutor
import org.usvm.instrumentation.executor.UTestExecutionOptions
import org.usvm.instrumentation.instrumentation.NoInstrumentationFactory
import org.usvm.instrumentation.rd.InstrumentedProcess
import org.usvm.instrumentation.testcase.api.UTestExecutionSuccessResult
import org.usvm.test.api.UTest
import java.io.File
import kotlin.time.Duration

class SpringTestReproducer(
    private val options: JcConcreteMachineOptions,
    private val cp: JcClasspath
) {
    private fun createExecutor(): UTestConcreteExecutor {
        val reproducingLocations = System.getenv("usvm.jvm.springTestDeps.paths").split(";")
        val locations = (options.projectLocations + options.dependenciesLocations).map { it.path } + reproducingLocations
        val opts = UTestExecutionOptions(execMode = InstrumentedProcess.UTestExecMode.RESULT_ONLY)
        val executor = UTestConcreteExecutor(
            instrumentationClassFactory = NoInstrumentationFactory::class,
            testingProjectClasspath = locations.joinToString(File.pathSeparator),
            jcClasspath = cp,
            timeout = Duration.INFINITE,
            opts = opts
        )
        runBlocking { executor.ensureRunnerAlive() }
        return executor
    }

    private val executor = createExecutor()

    fun reproduce(test: UTest): Boolean {
        val result = executor.executeSync(test)
        return result is UTestExecutionSuccessResult
    }

    fun kill() {
        executor.close()
    }
}
