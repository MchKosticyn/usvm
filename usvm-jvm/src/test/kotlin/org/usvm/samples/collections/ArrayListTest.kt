package org.usvm.samples.collections

import org.junit.jupiter.api.Test
import org.usvm.samples.JavaMethodTestRunner
import org.usvm.samples.approximations.ApproximationsTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ArrayListCopyTest : ApproximationsTestRunner()  {
    @Test
    fun testArrayListConstructorTest() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayListSamples::arrayListCorrectCopy,
            ignoreNumberOfAnalysisResults,
            invariants = arrayOf(
                { arr, result -> result.getOrNull() != false },
            )
        )
    }

    @Test
    fun testArrayListSublistIteration() {
        checkDiscoveredPropertiesWithExceptions(
            ArrayListSamples::arrayListSublistIteration,
            ignoreNumberOfAnalysisResults,
            invariants = arrayOf(
                { arr, result -> result.getOrNull() == true },
            )
        )
    }
}
