package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ABCTest : MocksTestRunner() {
    @Test
    fun testCompute() {
        checkDiscoveredPropertiesWithExceptions(
            TestABC::compute,
            ignoreNumberOfAnalysisResults,
            { _, _, _, _, r -> r.getOrNull() == null }
        )
    }
}
