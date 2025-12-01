package org.usvm.samples

import machine.JcMocksMachine
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.api.targets.JcTarget
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


class CalculatorTest : MocksTestRunner() {
    override fun createMachine(
        cp: JcClasspath,
        options: UMachineOptions,
        interpreterObserver: JcInterpreterObserver?
    ): JcMachine {
        return JcMocksMachine(cp, options, interpreterObserver = interpreterObserver)
    }

    @Test
    fun testCompute() {
        checkDiscoveredPropertiesWithExceptions(
            Calculator::compute,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.getOrNull() == 0 },
//            { _, _, _, r -> r.isException<AssertionError>() }


        )
    }
}
