package machine

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

open class JcMocksMachine(
    cp: JcClasspath,
    options: UMachineOptions,
    jcMachineOptions: JcMachineOptions = JcMachineOptions(),
    interpreterObserver: JcInterpreterObserver? = null,
) : JcMachine(cp, options, jcMachineOptions, interpreterObserver) {
    override fun createInterpreter(): JcInterpreter {
        return JcMocksInterpreter(
            ctx,
            applicationGraph,
            jcMachineOptions,
            interpreterObserver
        )
    }
    fun createTargetedPS() { }
}