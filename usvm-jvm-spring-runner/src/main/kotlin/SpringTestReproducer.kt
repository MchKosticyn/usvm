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
    private val cp: JcClasspath,
    private val memoryLimit: Int = 3
) {
    private fun createExecutor(): UTestConcreteExecutor {
        // TODO: #CM add spring approximations instead of java.stdlib
        val approximations = System.getenv("usvm.jvm.spring.approximations.jar.path")
        val dependencyJars = options.dependenciesLocations.map { it.jarOrFolder }
        val dependencies = TestDependenciesManager.getTestDependencies(dependencyJars)
        val locations = options.projectLocations.map { it.path } +
                dependencies.map { it.path } +
                listOf(approximations)
        val opts = UTestExecutionOptions(execMode = InstrumentedProcess.UTestExecMode.RESULT_ONLY)
        val executor = UTestConcreteExecutor(
            instrumentationClassFactory = NoInstrumentationFactory::class,
            testingProjectClasspath = locations.joinToString(File.pathSeparator),
            jcClasspath = cp,
            timeout = Duration.INFINITE,
            opts = opts,
            memoryLimit = memoryLimit,
            allowForDebugger = false,
        )
        runBlocking { executor.ensureRunnerAlive() }
        return executor
    }

    private var executor: UTestConcreteExecutor? = null

    fun reproduce(test: UTest): Boolean {
        if (executor == null)
            executor = createExecutor()

        val result = executor!!.executeSync(test)
        return result is UTestExecutionSuccessResult
    }

    fun kill() {
        executor?.close()
    }
}
