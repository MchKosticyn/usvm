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

class ReflectionIntegerBoxTest : ApproximationsTestRunner() {

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
            ReflectionIntegerBox::test,
            ignoreNumberOfAnalysisResults,
            invariants = arrayOf(
                { i, r -> if (i != 3) r.getOrNull() == i + 1 else r.getOrNull() == 3 }
            )
        )
    }
}
