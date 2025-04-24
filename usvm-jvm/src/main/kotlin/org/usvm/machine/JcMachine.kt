package org.usvm.machine

import mu.KLogging
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.humanReadableSignature
import org.jacodb.api.jvm.ext.methods
import org.usvm.CoverageZone
import org.usvm.StateCollectionStrategy
import org.usvm.UMachine
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.api.targets.JcTarget
import org.usvm.forkblacklists.TargetsReachableForkBlackList
import org.usvm.forkblacklists.UForkBlackList
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcMethodResult
import org.usvm.machine.state.JcState
import org.usvm.machine.state.lastStmt
import org.usvm.ps.StateLoopTracker
import org.usvm.ps.createPathSelector
import org.usvm.statistics.CompositeUMachineObserver
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.StatisticsByMethodPrinter
import org.usvm.statistics.StepsStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.TransitiveCoverageZoneObserver
import org.usvm.statistics.UMachineObserver
import org.usvm.statistics.collectors.AllStatesCollector
import org.usvm.statistics.collectors.CoveredNewStatesCollector
import org.usvm.statistics.collectors.StatesCollector
import org.usvm.statistics.collectors.TargetsReachedStatesCollector
import org.usvm.statistics.constraints.SoftConstraintsObserver
import org.usvm.statistics.distances.CallGraphStatistics
import org.usvm.statistics.distances.CfgStatistics
import org.usvm.statistics.distances.CfgStatisticsImpl
import org.usvm.statistics.distances.InterprocDistance
import org.usvm.statistics.distances.InterprocDistanceCalculator
import org.usvm.statistics.distances.MultiTargetDistanceCalculator
import org.usvm.statistics.distances.PlainCallGraphStatistics
import org.usvm.stopstrategies.createStopStrategy
import org.usvm.util.originalInst

val logger = object : KLogging() {}.logger

open class JcMachine(
    cp: JcClasspath,
    private val options: UMachineOptions,
    protected val jcMachineOptions: JcMachineOptions = JcMachineOptions(),
    protected val interpreterObserver: JcInterpreterObserver? = null,
) : UMachine<JcState>() {
    protected val applicationGraph = JcApplicationGraph(cp)

    private val typeSystem = JcTypeSystem(cp, options.typeOperationsTimeout)
    private val components = JcComponents(typeSystem, options)
    protected val ctx = JcContext(cp, components)

    private val cfgStatistics = CfgStatisticsImpl(applicationGraph)

    protected open fun createInterpreter(): JcInterpreter {
        return JcInterpreter(ctx, applicationGraph, jcMachineOptions, interpreterObserver)
    }

    protected open fun createPathSelector(
        initialStates: Map<JcMethod, JcState>,
        options: UMachineOptions,
        timeStatistics: TimeStatistics<JcMethod, JcState>,
        coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState>,
        callGraphStatistics: CallGraphStatistics<JcMethod>,
        loopStatisticFactory: () -> StateLoopTracker<*, JcInst, JcState>? = { null },
        wrappingPathSelector: (UPathSelector<JcState>) -> UPathSelector<JcState> = { it }
    ): UPathSelector<JcState> {
        return createPathSelector(
            initialStates,
            options,
            applicationGraph,
            timeStatistics,
            { coverageStatistics },
            { transparentCfgStatistics() },
            { callGraphStatistics },
            { JcLoopTracker() },
            wrappingPathSelector
        )
    }

    protected open fun ignoreMethod(methodsToTrackCoverage: Set<JcMethod>): (JcMethod) -> Boolean {
        return when (options.coverageZone) {
            CoverageZone.CLASS -> { m: JcMethod -> !methodsToTrackCoverage.contains(m) }
            CoverageZone.TRANSITIVE -> { _ -> false }
            CoverageZone.METHOD -> throw IllegalStateException()
        }
    }

    protected open fun createObservers(
        coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState>,
        timeStatistics: TimeStatistics<JcMethod, JcState>,
        stepsStatistics: StepsStatistics<JcMethod, JcState>,
        methodsToTrackCoverage: Set<JcMethod>,
        statesCollector: StatesCollector<JcState>,
        methods: List<JcMethod>,
        pathSelector: UPathSelector<JcState>
    ): List<UMachineObserver<JcState>> {
        val observers = mutableListOf<UMachineObserver<JcState>>(coverageStatistics)
        observers.add(timeStatistics)
        observers.add(stepsStatistics)
        if (interpreterObserver is UMachineObserver<*>) {
            @Suppress("UNCHECKED_CAST")
            observers.add(interpreterObserver as UMachineObserver<JcState>)
        }

        if (options.coverageZone != CoverageZone.METHOD) {
            observers.add(
                TransitiveCoverageZoneObserver(
                    initialMethods = methodsToTrackCoverage,
                    methodExtractor = { state -> state.lastStmt.location.method },
                    addCoverageZone = { coverageStatistics.addCoverageZone(it) },
                    ignoreMethod = ignoreMethod(methodsToTrackCoverage)
                )
            )
        }

        observers.add(statesCollector)

        if (options.useSoftConstraints) {
            observers.add(SoftConstraintsObserver())
        }

        if (logger.isInfoEnabled) {
            observers.add(
                StatisticsByMethodPrinter(
                    { methods },
                    logger::info,
                    { it.humanReadableSignature },
                    coverageStatistics,
                    timeStatistics,
                    stepsStatistics
                )
            )
        }

        if (logger.isDebugEnabled) {
            observers.add(JcDebugProfileObserver(pathSelector))
        }

        return observers
    }

    protected open fun methodsToTrackCoverage(methods: List<JcMethod>): Set<JcMethod> {
        return when (options.coverageZone) {
            CoverageZone.METHOD,
            CoverageZone.TRANSITIVE -> methods.toSet()
            // TODO: more adequate method filtering. !it.isConstructor is used to exclude default constructor which is often not covered
            CoverageZone.CLASS -> methods.flatMap { method ->
                method.enclosingClass.methods.filter {
                    it.enclosingClass == method.enclosingClass && !it.isConstructor
                }
            }.toSet() + methods
        }
    }

    protected open fun createTimeStatistics(): TimeStatistics<JcMethod, JcState> = TimeStatistics()

    fun analyze(methods: List<JcMethod>, targets: List<JcTarget> = emptyList()): List<JcState> {
        logger.debug("{}.analyze({})", this, methods)
        val interpreter = createInterpreter()
        val initialStates = mutableMapOf<JcMethod, JcState>()
        methods.forEach { initialStates[it] = interpreter.getInitialState(it, targets) }

        val methodsToTrackCoverage = methodsToTrackCoverage(methods)

        val coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState> = CoverageStatistics(
            methodsToTrackCoverage,
            applicationGraph
        )

        val callGraphStatistics =
            when (options.targetSearchDepth) {
                0u -> PlainCallGraphStatistics()
                else -> JcCallGraphStatistics(
                    options.targetSearchDepth,
                    applicationGraph,
                    typeSystem.topTypeStream(),
                    subclassesToTake = 10
                )
            }

        val timeStatistics = createTimeStatistics()

        val pathSelector = createPathSelector(
            initialStates,
            options,
            timeStatistics,
            coverageStatistics,
            callGraphStatistics
        )

        val statesCollector =
            when (options.stateCollectionStrategy) {
                StateCollectionStrategy.COVERED_NEW -> CoveredNewStatesCollector<JcState>(coverageStatistics) {
                    it.methodResult is JcMethodResult.JcException
                }

                StateCollectionStrategy.REACHED_TARGET -> TargetsReachedStatesCollector()
                StateCollectionStrategy.ALL -> AllStatesCollector()
            }

        val stepsStatistics = StepsStatistics<JcMethod, JcState>()

        val stopStrategy = createStopStrategy(
            options,
            targets,
            timeStatisticsFactory = { timeStatistics },
            stepsStatisticsFactory = { stepsStatistics },
            coverageStatisticsFactory = { coverageStatistics },
            getCollectedStatesCount = { statesCollector.collectedStates.size }
        )

        val observers = createObservers(
            coverageStatistics,
            timeStatistics,
            stepsStatistics,
            methodsToTrackCoverage,
            statesCollector,
            methods,
            pathSelector
        )

        // TODO: use the same calculator which is used for path selector
        if (targets.isNotEmpty()) {
            val distanceCalculator = MultiTargetDistanceCalculator<JcMethod, JcInst, InterprocDistance> { stmt ->
                InterprocDistanceCalculator(
                    targetLocation = stmt,
                    applicationGraph = applicationGraph,
                    cfgStatistics = cfgStatistics,
                    callGraphStatistics = callGraphStatistics
                )
            }
            interpreter.forkBlackList =
                TargetsReachableForkBlackList(distanceCalculator, shouldBlackList = { isInfinite })
        } else {
            interpreter.forkBlackList = UForkBlackList.createDefault()
        }

        run(
            interpreter,
            pathSelector,
            observer = CompositeUMachineObserver(observers.distinct()),
            isStateTerminated = ::isStateTerminated,
            stopStrategy = stopStrategy,
        )

        return statesCollector.collectedStates
    }

    fun analyze(method: JcMethod, targets: List<JcTarget> = emptyList()): List<JcState> =
        analyze(listOf(method), targets)

    /**
     * Returns a wrapper for the [cfgStatistics] that ignores [JcTransparentInstruction]s.
     * Instead of calculating statistics for them, it just takes the statistics for
     * their original instructions.
     */
    private fun transparentCfgStatistics() = object : CfgStatistics<JcMethod, JcInst> {
        override fun getShortestDistance(method: JcMethod, stmtFrom: JcInst, stmtTo: JcInst): UInt {
            return cfgStatistics.getShortestDistance(method, stmtFrom.originalInst(), stmtTo.originalInst())
        }

        override fun getShortestDistanceToExit(method: JcMethod, stmtFrom: JcInst): UInt {
            return cfgStatistics.getShortestDistanceToExit(method, stmtFrom.originalInst())
        }
    }

    private fun isStateTerminated(state: JcState): Boolean {
        return state.callStack.isEmpty()
    }

    override fun close() {
        components.close()
    }
}
