package org.usvm.samples

import machine.JcMocksMachine
import org.jacodb.api.jvm.JcClasspath
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.PathSelectionStrategy
import org.usvm.PathSelectorCombinationStrategy
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.approximations.ApproximationsTestRunner
import org.usvm.util.Options
import org.usvm.util.UsvmTest
import org.usvm.util.isException


class CalculatorTest : JavaMethodTestRunner() {
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
            Calculator::testCompute,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.getOrNull() == 0 },
//            { _, _, _, r -> r.isException<AssertionError>() }


        )
    }
}
