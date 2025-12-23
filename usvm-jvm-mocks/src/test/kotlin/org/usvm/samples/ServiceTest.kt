package org.usvm.samples

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ServiceTest : MocksTestRunner() {
    @BeforeEach
    fun reset() {
        cleanUp()
    }

    @Test
    fun testCalc() {
        checkDiscoveredPropertiesWithExceptions(
            TestService::compute,
            ignoreNumberOfAnalysisResults,
            { _, _, r -> r.getOrNull() == null }
        )
    }
}
