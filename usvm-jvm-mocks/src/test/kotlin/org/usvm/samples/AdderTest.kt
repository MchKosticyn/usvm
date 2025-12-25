package org.usvm.samples

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class AdderTest : MocksTestRunner() {
    @BeforeEach
    fun reset() {
        cleanUp()
    }

    @Test
    fun testCompute() {
        checkDiscoveredPropertiesWithExceptions(
            TestAdder::compute,
            ignoreNumberOfAnalysisResults,
            { _, _, _, r -> r.getOrNull() == null }
        )
    }
}
