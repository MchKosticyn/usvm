package org.usvm.samples.strings11

import org.junit.jupiter.api.Test
import org.usvm.PathSelectionStrategy
import org.usvm.samples.approximations.ApproximationsTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults
import kotlin.time.Duration

class StringConcatApproximationsTest : ApproximationsTestRunner() {

    init {
        options = options.copy(stepsFromLastCovered = null, timeout = Duration.INFINITE, pathSelectionStrategies = listOf(PathSelectionStrategy.DFS))
    }

    @Test
    fun testConcatArguments() {
        checkDiscoveredPropertiesWithExceptions(
            StringConcat::checkStringBuilder,
            ignoreNumberOfAnalysisResults,
            invariants = arrayOf({ _, _, _, r -> r.getOrNull() == true })
        )
    }

    @Test
    fun testConcatArguments1() {
        checkDiscoveredPropertiesWithExceptions(
            StringConcat::wip,
            ignoreNumberOfAnalysisResults,
            invariants = arrayOf({ _, r -> r.getOrNull() == true })
        )
    }

    @Test
    fun testConcatArguments2() {
        checkDiscoveredPropertiesWithExceptions(
            StringConcat::kek,
            ignoreNumberOfAnalysisResults,
            invariants = arrayOf({ _, r -> r.getOrNull() == true })
        )
    }
}
