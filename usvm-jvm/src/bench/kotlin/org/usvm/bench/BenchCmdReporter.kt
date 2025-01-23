package org.usvm.bench

import mu.KLogging
import org.jacodb.api.jvm.JcMethod
import org.usvm.UMachineOptions
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.state.JcState
import kotlin.math.log

private val logger = object : KLogging() {}.logger

object BenchCmdReporter : BenchStatisticsReporter {
    override fun reportConfig(
        timestamp: Long,
        options: UMachineOptions,
        jcMachineOptions: JcMachineOptions,
        comment: String,
        randomSeed: Int,
        samplesToTake: Int,
        projectName: String
    ): String {
        logger.info {
            "BENCHMARK STARTED: $projectName"
        }
        return ""
    }

    override fun reportResult(
        jcMethod: JcMethod,
        states: List<JcState>,
        configId: String,
        coverage: Float,
        timeElapsedMillis: Long,
        stepsMade: Int,
        statesInPathSelector: Int
    ) {
        val result = StringBuilder().apply {
            appendLine("METHOD EXPLORATION STATS: ${jcMethod.enclosingClass}.${jcMethod.name}")
            appendLine("\tMilliseconds elapsed: $timeElapsedMillis")
            appendLine("\tSuccessful states: ${states.filterNot { it.isExceptional }.size}")
            appendLine("\tExceptional states: ${states.filter { it.isExceptional }.size}")
            appendLine("\tStates remaining in PS: $statesInPathSelector")
            appendLine("\tSteps made: $stepsMade")
            appendLine("\tCoverage: $coverage")
        }
        logger.info { result.toString() }
    }

    override fun reportInternalFailure(jcMethod: JcMethod, e: Throwable, configId: String) {
        logger.error {
            "INTERNAL FAIL:  ${jcMethod.enclosingClass}.${jcMethod.name}\n$e"
        }
    }
}
