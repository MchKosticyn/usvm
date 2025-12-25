package org.usvm.samples

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class CalcTest : MocksTestRunner() {
    @BeforeEach
    fun reset() {
        cleanUp()
    }

    @Test
    fun testCalc() {
        checkDiscoveredPropertiesWithExceptions(
            TestCalc::compute,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.getOrNull() == null }
        )
    }
}
