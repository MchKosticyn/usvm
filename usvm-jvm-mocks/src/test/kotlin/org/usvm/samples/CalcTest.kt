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
//            { _, a, _, r -> a <= 0 && r.getOrNull() == null },
        )
    }
//    @Test
//    fun testCalc2() {
//        val test = createTest(TestCalc::compute)
//        println(test)
//    }
}
