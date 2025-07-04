package org.usvm.samples

import features.JcEncodingFeature
import machine.JcConcreteMachine
import org.jacodb.api.jvm.JcClasspath
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.samples.approximations.ApproximationsTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class LambdasTest : ApproximationsTestRunner() {

    override val cp by lazy {
        JacoDBContainer(jacodbCpKey, classpath, listOf(JcEncodingFeature)).cp
    }

    override fun createMachine(
        cp: JcClasspath,
        options: UMachineOptions,
        interpreterObserver: JcInterpreterObserver?
    ): JcMachine {
        return JcConcreteMachine(cp, options, interpreterObserver = interpreterObserver)
    }

    @Test
    fun test1() {
        checkDiscoveredPropertiesWithExceptions(
            Lambdas::test,
            ignoreNumberOfAnalysisResults,
            invariants = arrayOf(
                { _, r -> r.getOrNull() == true }
            )
        )
    }
}
