package org.usvm.bench

import org.jacodb.api.jvm.JcMethod
import org.usvm.UMachineOptions
import org.usvm.machine.JcMachineOptions
import org.usvm.machine.state.JcState

object BenchMockReporter : BenchStatisticsReporter {
    override fun reportConfig(
        timestamp: Long,
        options: UMachineOptions,
        jcMachineOptions: JcMachineOptions,
        comment: String,
        randomSeed: Int,
        samplesToTake: Int,
        projectName: String
    ): String { return "mock_config_id" }

    override fun reportResult(
        jcMethod: JcMethod,
        states: List<JcState>,
        configId: String,
        coverage: Float,
        timeElapsedMillis: Long,
        stepsMade: Int,
        statesInPathSelector: Int
    ) { }

    override fun reportInternalFailure(jcMethod: JcMethod, e: Throwable, configId: String) { }
}
