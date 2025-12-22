package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class AdderTest : MocksTestRunner() {

    @Test
    fun testCompute() {
        checkDiscoveredPropertiesWithExceptions(
            TestAdder::compute,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.getOrNull() == null }
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
