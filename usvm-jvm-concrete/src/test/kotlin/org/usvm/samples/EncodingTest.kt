package org.usvm.samples

import machine.JcConcreteMachine
import org.jacodb.api.jvm.JcClasspath
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.JcTest
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.samples.approximations.ApproximationsTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.util.getJcMethodByName
import kotlin.reflect.KFunction

class EncodingTest : ApproximationsTestRunner() {

    override fun createMachine(
        cp: JcClasspath,
        options: UMachineOptions,
        interpreterObserver: JcInterpreterObserver?
    ): JcMachine {
        return JcConcreteMachine(cp, options, interpreterObserver = interpreterObserver)
    }

    @Test
    fun test1() {
        checkDiscoveredPropertiesWithExceptions(
            Encoding::test,
            ignoreNumberOfAnalysisResults,
            invariants = arrayOf(
                { _, r -> r.getOrNull() == 0 }
            )
        )
    }
}
