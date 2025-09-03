package machine

import machine.ps.JcConcreteMachineWeighters
import machine.ps.JcConcreteWeightedPathSelector
import machine.ps.JcConcreteWrappingPathSelector
import machine.ps.weighters.JcConcreteBacktrackWeighter
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.machine.JcComponents
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcState
import org.usvm.ps.StateLoopTracker
import org.usvm.statistics.CoverageStatistics
import org.usvm.statistics.TimeStatistics
import org.usvm.statistics.distances.CallGraphStatistics

open class JcConcreteMachine(
    cp: JcClasspath,
    options: UMachineOptions,
    jcMachineOptions: JcMachineOptions = JcMachineOptions(),
    protected val jcConcreteMachineOptions: JcConcreteMachineOptions = JcConcreteMachineOptions(),
    interpreterObserver: JcInterpreterObserver? = null,
) : JcMachine(cp, options, jcMachineOptions, interpreterObserver) {

    override fun createContext(
        cp: JcClasspath,
        components: JcComponents
    ): JcConcreteContext {
        return JcConcreteContext(cp, components)
    }

    override fun createInterpreter(): JcInterpreter {
        return JcConcreteInterpreter(
            ctx,
            applicationGraph,
            jcMachineOptions,
            jcConcreteMachineOptions,
            interpreterObserver
        )
    }

    fun createWeightedPathSelector(
        initialStates: Map<JcMethod, JcState>,
        options: UMachineOptions,
        timeStatistics: TimeStatistics<JcMethod, JcState>,
        coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState>,
        callGraphStatistics: CallGraphStatistics<JcMethod>,
        loopStatisticFactory: () -> StateLoopTracker<*, JcInst, JcState>?,
        weighters: JcConcreteMachineWeighters,
        basePathSelectors: (() -> List<UPathSelector<JcState>>)?,
        wrappingPathSelector: (UPathSelector<JcState>) -> UPathSelector<JcState>
    ): UPathSelector<JcState> {
        var concretePs: JcConcreteWeightedPathSelector? = null
        val concreteBasePathSelectors = basePathSelectors ?: {
            val ps = JcConcreteWeightedPathSelector(weighters)
            concretePs = ps
            listOf(ps)
        }
        val resultPs = super.createPathSelector(
            initialStates,
            options,
            timeStatistics,
            coverageStatistics,
            callGraphStatistics,
            loopStatisticFactory,
            concreteBasePathSelectors,
            wrappingPathSelector
        )
        concretePs?.setAddStateAction { state ->
            resultPs.add(listOf(state))
        }

        return resultPs
    }

    fun createWrappingPathSelector(
        initialStates: Map<JcMethod, JcState>,
        options: UMachineOptions,
        timeStatistics: TimeStatistics<JcMethod, JcState>,
        coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState>,
        callGraphStatistics: CallGraphStatistics<JcMethod>,
        loopStatisticFactory: () -> StateLoopTracker<*, JcInst, JcState>?,
        basePathSelectors: (() -> List<UPathSelector<JcState>>)?,
        wrappingPathSelector: (UPathSelector<JcState>) -> UPathSelector<JcState>
    ): UPathSelector<JcState> {
        var concretePs: JcConcreteWrappingPathSelector? = null
        val resultPs = super.createPathSelector(
            initialStates,
            options,
            timeStatistics,
            coverageStatistics,
            callGraphStatistics,
            loopStatisticFactory,
            basePathSelectors
        ) {
            val ps = JcConcreteWrappingPathSelector(it)
            concretePs = ps
            wrappingPathSelector(ps)
        }
        check(concretePs != null)
        concretePs!!.setAddStateAction { state ->
            resultPs.add(listOf(state))
        }
        return resultPs
    }

    override fun createPathSelector(
        initialStates: Map<JcMethod, JcState>,
        options: UMachineOptions,
        timeStatistics: TimeStatistics<JcMethod, JcState>,
        coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState>,
        callGraphStatistics: CallGraphStatistics<JcMethod>,
        loopStatisticFactory: () -> StateLoopTracker<*, JcInst, JcState>?,
        basePathSelectors: (() -> List<UPathSelector<JcState>>)?,
        wrappingPathSelector: (UPathSelector<JcState>) -> UPathSelector<JcState>
    ): UPathSelector<JcState> {
        return createWrappingPathSelector(
            initialStates,
            options,
            timeStatistics,
            coverageStatistics,
            callGraphStatistics,
            loopStatisticFactory,
            basePathSelectors,
            wrappingPathSelector
        )
    }
}
