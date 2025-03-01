package machine

import isSpringController
import isSpringFilter
import isSpringHandlerInterceptor
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.impl.features.classpaths.JcUnknownMethod
import org.usvm.UMachineOptions
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.interpreter.JcInterpreter
import org.usvm.util.classesOfLocations

class JcSpringMachine(
    cp: JcClasspath,
    options: UMachineOptions,
    jcMachineOptions: JcMachineOptions = JcMachineOptions(),
    jcConcreteMachineOptions: JcConcreteMachineOptions,
    private val jcSpringMachineOptions: JcSpringMachineOptions,
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
}
