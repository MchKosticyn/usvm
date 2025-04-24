package machine

import isSpringController
import isSpringFilter
import isSpringHandlerInterceptor
import machine.state.JcSpringState
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.impl.features.classpaths.JcUnknownMethod
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcLoopTracker
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcState
import org.usvm.ps.StateLoopTracker
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.StepsStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.collectors.StatesCollector
import org.usvm.statistics.distances.CallGraphStatistics
import org.usvm.util.classesOfLocations
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

class JcSpringMachine(
    cp: JcClasspath,
    options: UMachineOptions,
    jcMachineOptions: JcMachineOptions = JcMachineOptions(),
    jcConcreteMachineOptions: JcConcreteMachineOptions,
    private val jcSpringMachineOptions: JcSpringMachineOptions,
    private val testObserver: JcSpringTestObserver?,
    interpreterObserver: JcInterpreterObserver? = null,
) : JcConcreteMachine(cp, options, jcMachineOptions, jcConcreteMachineOptions, interpreterObserver) {

    override fun createInterpreter(): JcInterpreter {
        return JcSpringInterpreter(
            ctx,
            applicationGraph,
            jcMachineOptions,
            jcConcreteMachineOptions,
            jcSpringMachineOptions,
            interpreterObserver
        )
    }

    override fun ignoreMethod(methodsToTrackCoverage: Set<JcMethod>): (JcMethod) -> Boolean {
        return { m -> !methodsToTrackCoverage.contains(m) }
    }

    override fun methodsToTrackCoverage(methods: List<JcMethod>): Set<JcMethod> {
        return ctx.classesOfLocations(jcConcreteMachineOptions.projectLocations)
            .filter { it.isSpringController || it.isSpringFilter || it.isSpringHandlerInterceptor }
            .flatMap { it.declaredMethods }
            .filterNot { it is JcUnknownMethod || it.isConstructor }
            .toSet()
    }

    @Suppress("UNCHECKED_CAST")
    override fun createObservers(
        coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState>,
        timeStatistics: TimeStatistics<JcMethod, JcState>,
        stepsStatistics: StepsStatistics<JcMethod, JcState>,
        methodsToTrackCoverage: Set<JcMethod>,
        statesCollector: StatesCollector<JcState>,
        methods: List<JcMethod>,
        pathSelector: UPathSelector<JcState>
    ): List<UMachineObserver<JcState>> {
        val observers = super.createObservers(
            coverageStatistics,
            timeStatistics,
            stepsStatistics,
            methodsToTrackCoverage,
            statesCollector,
            methods,
            pathSelector
        )

        if (testObserver != null)
            return observers + (testObserver as UMachineObserver<JcState>)

        return observers
    }

    override fun createTimeStatistics(): TimeStatistics<JcMethod, JcState> = JcSpringTimeStatistics()

    override fun createPathSelector(
        initialStates: Map<JcMethod, JcState>,
        options: UMachineOptions,
        timeStatistics: TimeStatistics<JcMethod, JcState>,
        coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState>,
        callGraphStatistics: CallGraphStatistics<JcMethod>,
        loopStatisticFactory: () -> StateLoopTracker<*, JcInst, JcState>?,
        wrappingPathSelector: (UPathSelector<JcState>) -> UPathSelector<JcState>
    ): UPathSelector<JcState> {
        val springLoopTracker = {
            val baseLoopTracker = loopStatisticFactory() as? JcLoopTracker ?: JcLoopTracker()
            JcSpringMachineLoopTracker(baseLoopTracker)
        }
        val springPs = { pathSelector: UPathSelector<JcState> ->
            val springTimeStatistics = timeStatistics as JcSpringTimeStatistics
            JcStatePathTimeoutPathSelector(springTimeStatistics, pathSelector, options.timeout)
        }
        return super.createPathSelector(
            initialStates,
            options,
            timeStatistics,
            coverageStatistics,
            callGraphStatistics,
            springLoopTracker,
            springPs
        )
    }
}
