package machine

import machine.ps.JcConcreteMemoryPathSelector
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.machine.JcComponents
import org.usvm.machine.JcContext
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.JcTypeSystem
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

    override fun createComponents(
        typeSystem: JcTypeSystem,
        options: UMachineOptions
    ): JcConcreteComponents {
        return JcConcreteComponents(typeSystem, options)
    }

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

    override fun createPathSelector(
        initialStates: Map<JcMethod, JcState>,
        options: UMachineOptions,
        timeStatistics: TimeStatistics<JcMethod, JcState>,
        coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState>,
        callGraphStatistics: CallGraphStatistics<JcMethod>,
        loopStatisticFactory: () -> StateLoopTracker<*, JcInst, JcState>?,
        wrappingPathSelector: (UPathSelector<JcState>) -> UPathSelector<JcState>
    ): UPathSelector<JcState> {
        var concretePs: JcConcreteMemoryPathSelector? = null
        val resultPs = super.createPathSelector(
            initialStates,
            options,
            timeStatistics,
            coverageStatistics,
            callGraphStatistics,
            loopStatisticFactory
        ) {
            val ps = JcConcreteMemoryPathSelector(it)
            concretePs = ps
            wrappingPathSelector(ps)
        }
        check(concretePs != null)
        concretePs!!.setAddStateAction { state ->
            resultPs.add(listOf(state))
        }
        return resultPs
    }
}
