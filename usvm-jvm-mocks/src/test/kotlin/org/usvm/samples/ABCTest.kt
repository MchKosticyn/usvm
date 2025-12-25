package org.usvm.samples

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ABCTest : MocksTestRunner() {
    @BeforeEach
    fun reset() {
        cleanUp()
    }

    @Test
    fun testCompute() {
        checkDiscoveredPropertiesWithExceptions(
            TestABC::compute,
            ignoreNumberOfAnalysisResults,
            { _, _, _, _, r -> r.getOrNull() == null }
        )
    }
}
