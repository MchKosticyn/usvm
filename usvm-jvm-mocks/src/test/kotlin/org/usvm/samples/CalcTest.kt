package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class CalcTest : MocksTestRunner() {

    @Test
    fun testCalc() {
        checkDiscoveredPropertiesWithExceptions(
            TestCalc::compute,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.getOrNull() == null }
        )
    }
}
