package org.usvm.samples

import features.JcEncodingFeature
import io.mockk.core.ValueClassSupport.boxedValue
import machine.JcConcreteMachine
import org.jacodb.api.jvm.JcClasspath
import org.junit.jupiter.api.Test
import org.usvm.UMachineOptions
import org.usvm.machine.JcInterpreterObserver
import org.usvm.machine.JcMachine
import org.usvm.samples.approximations.ApproximationsTestRunner
import org.usvm.test.util.checkers.ignoreNumberOfAnalysisResults

class ConstructorsTest : ApproximationsTestRunner() {

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
            Constructors::reflectionConstructorCall,
            ignoreNumberOfAnalysisResults,
            invariants = arrayOf(
                { _, result -> result.isSuccess },
                { input, result -> result.isSuccess && (result.boxedValue as SampleClass).a == input },
                { input, result -> result.isSuccess && (result.boxedValue as SampleClass).b == input + 10 },
            )
        )
    }
}
