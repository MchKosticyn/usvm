package machine

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcType
import org.usvm.UInterpreter
import org.usvm.UMachineOptions
import org.usvm.UPathSelector
import org.usvm.logger
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.machine.state.JcState
import org.usvm.model.UModelBase
import org.usvm.solver.USatResult
import org.usvm.solver.USolverResult
import org.usvm.statistics.UMachineObserver
import org.usvm.stopstrategies.StopStrategy
import org.usvm.util.bracket
import org.usvm.util.debug

private fun JcState.isSat() : Boolean {
    if (models.isNotEmpty()) {
        return true
    }

    return verify() is USatResult
}

private fun JcState.verify(): USolverResult<UModelBase<JcType>> {
    val solver = ctx.solver<JcType>()
    val solverResult = solver.check(pathConstraints)

    if (solverResult is USatResult) {
        models = listOf(solverResult.model)
    }

    return solverResult
}
open class JcMocksMachine(
    cp: JcClasspath,
    options: UMachineOptions,
    jcMachineOptions: JcMachineOptions = JcMachineOptions(),
    interpreterObserver: JcInterpreterObserver? = null,
) : JcMachine(cp, options, jcMachineOptions, interpreterObserver) {
    override val components = JcMocksComponents(typeSystem, options)
    override fun createInterpreter(): JcInterpreter {
        return JcMocksInterpreter(
            ctx,
            applicationGraph,
            jcMachineOptions,
            interpreterObserver
        )
    }
    override fun run(
        interpreter: UInterpreter<JcState>,
        pathSelector: UPathSelector<JcState>,
        observer: UMachineObserver<JcState>,
        isStateTerminated: (JcState) -> Boolean,
        stopStrategy: StopStrategy
) {
        logger.debug().bracket("$this.run($interpreter, ${pathSelector::class.simpleName})") {
            observer.onMachineStarted()
            try {
                while (!pathSelector.isEmpty() && !stopStrategy.shouldStop()) {
                val state = pathSelector.peek()
                observer.onStatePeeked(state)

                val (forkedStates, stateAlive) = try {
                    interpreter.step(state)
                } catch (e: Throwable) {
                    logger.error(e) { "Step failed" }
                    observer.onState(state, forks = emptySequence())
                    pathSelector.remove(state)
                    observer.onStateTerminated(state, stateReachable = false)
                    continue
                }

                observer.onState(state, forkedStates)

                val originalStateAlive = stateAlive && !isStateTerminated(state)
                val aliveForkedStates = mutableListOf<JcState>()
                for (forkedState in forkedStates) {
                    if (!isStateTerminated(forkedState)) {
                        aliveForkedStates.add(forkedState)
                    } else {
                        // TODO: distinguish between states terminated by exception (runtime or user) and
                        //  those which just exited
                        if (forkedState.isSat()) {
                            observer.onStateTerminated(forkedState, stateReachable = true)
                        }
                    }
                }
                if (originalStateAlive) {
                    pathSelector.update(state)
                } else {
                    pathSelector.remove(state)
                    if (state.isSat()) {
                        observer.onStateTerminated(state, stateReachable = stateAlive)
                    }
                }

                if (aliveForkedStates.isNotEmpty()) {
                    pathSelector.add(aliveForkedStates)
                }
            }
        } finally {
//            printMockedMethodsValues()
            observer.onMachineStopped()
        }

        if (!pathSelector.isEmpty()) {
            val stopReason = stopStrategy.stopReason()
            logger.debug { stopReason }
        }
    }

}}