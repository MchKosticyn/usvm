package org.usvm.instrumentation.rd

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.usvm.instrumentation.instrumentation.JcInstructionTracer
import org.usvm.instrumentation.testcase.api.UTestExecutionState
import org.usvm.instrumentation.testcase.descriptor.StaticDescriptorsBuilder
import org.usvm.instrumentation.testcase.descriptor.Value2DescriptorConverter
import org.usvm.instrumentation.testcase.executor.UTestExpressionExecutor
import org.usvm.instrumentation.util.InstrumentationModuleConstants.testExecutorStaticsRollbackStrategy
import org.usvm.instrumentation.util.URLClassPathLoader
import org.usvm.test.api.UTestCall

class UTestExecutorCollectingResultOnly(
    jcClasspath: JcClasspath,
    ucp: URLClassPathLoader
) : UTestExecutor(jcClasspath, ucp) {

    override val staticDescriptorsBuilder: StaticDescriptorsBuilder? =
        when (testExecutorStaticsRollbackStrategy) {
            StaticsRollbackStrategy.ROLLBACK -> {
                StaticDescriptorsBuilder(workerClassLoader, initStateDescriptorBuilder)
            }
            else -> null
        }

    init {
        if (staticDescriptorsBuilder != null)
            workerClassLoader.setStaticDescriptorsBuilder(staticDescriptorsBuilder)
    }

    private val emptyExecState: UTestExecutionState =
        UTestExecutionState(null, emptyList(), mutableMapOf())

    override fun buildExecutionState(
        callMethodExpr: UTestCall,
        executor: UTestExpressionExecutor,
        descriptorBuilder: Value2DescriptorConverter,
        accessedStatics: MutableSet<Pair<JcField, JcInstructionTracer.StaticFieldAccessType>>
    ): UTestExecutionState = emptyExecState
}
