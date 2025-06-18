package org.usvm.samples

import machine.JcConcreteMachine
import org.jacodb.api.jvm.JcClasspath
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.samples.approximations.ApproximationsTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class StringsTest : ApproximationsTestRunner() {

    override fun createMachine(
        cp: JcClasspath,
        options: UMachineOptions,
        interpreterObserver: JcInterpreterObserver?
    ): JcMachine {
        return JcConcreteMachine(cp, options, interpreterObserver = interpreterObserver)
    }

    @Test
    fun checkConcatTest() {
        checkDiscoveredPropertiesWithExceptions(
            Strings::concatTest,
            ignoreNumberOfAnalysisResults,
            invariants = arrayOf(
                { _, result -> result.getOrNull() == true },
            )
        )
    }

    @Test
    fun checkStringEqualsTest() {
        checkDiscoveredPropertiesWithExceptions(
            Strings::isEqualToAaa,
            ignoreNumberOfAnalysisResults,
            invariants = arrayOf(
                { i, result -> result.getOrNull() == "String is Aaa" && i == "Aaa" },
                { i, result -> result.getOrNull() == "String is not Aaa" && i != "Aaa" }
            )
        )
    }
}
