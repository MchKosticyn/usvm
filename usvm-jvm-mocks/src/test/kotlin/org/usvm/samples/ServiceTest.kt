package org.usvm.samples

import org.junit.jupiter.api.Test
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ServiceTest : MocksTestRunner() {

    @Test
    fun testCalc() {
        checkDiscoveredPropertiesWithExceptions(
            TestService::compute,
            ignoreNumberOfAnalysisResults,
            { _, _, r -> r.getOrNull() == null }
        )
    }
}
