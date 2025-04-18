package machine

import machine.ps.JcConcreteMemoryPathSelector
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcLoopTracker
import org.usvm.machine.JcMachine
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcState
import org.usvm.ps.LoopLimiterPs
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
        timeStatistics: TimeStatistics<JcMethod, JcState>,
        coverageStatistics: CoverageStatistics<JcMethod, JcInst, JcState>,
        callGraphStatistics: CallGraphStatistics<JcMethod>
    ): UPathSelector<JcState> {
        val ps = super.createPathSelector(
            initialStates,
            timeStatistics,
            coverageStatistics,
            callGraphStatistics
        )
        // TODO: hack, redo #PS
        return LoopLimiterPs(JcConcreteMemoryPathSelector(ps), JcLoopTracker(), 2)
    }
}
