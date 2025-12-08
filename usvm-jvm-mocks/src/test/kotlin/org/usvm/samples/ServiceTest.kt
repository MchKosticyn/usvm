package org.usvm.samples


import machine.JcMocksMachine
import org.jacodb.api.jvm.JcClasspath
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults


class ServiceTest : MocksTestRunner() {
    override fun createMachine(
        cp: JcClasspath,
        options: UMachineOptions,
        interpreterObserver: JcInterpreterObserver?
    ): JcMachine {
        return JcMocksMachine(cp, options, interpreterObserver = interpreterObserver)
    }

    @Test
    fun testCalc() {
        checkDiscoveredPropertiesWithExceptions(
            TestService::compute,
            ignoreNumberOfAnalysisResults,
            { _, _, r -> r.getOrNull() == null },
        )
    }
}