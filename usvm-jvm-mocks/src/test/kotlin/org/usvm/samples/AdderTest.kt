package org.usvm.samples

import machine.JcMocksMachine
import org.jacodb.api.jvm.JcClasspath
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class AdderTest : MocksTestRunner() {

    @Test
    fun testCompute() {
        checkDiscoveredPropertiesWithExceptions(
            TestAdder::compute,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.getOrNull() == null },
        )
    }
//    @Test
//    fun testCompute2() {
//        checkDiscoveredPropertiesWithExceptions(
//            TestAdder2::compute,
//            ignoreNumberOfAnalysisResults,
//            { _, r -> r.getOrNull() == null },
//        )
//    }
}
