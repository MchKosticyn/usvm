package org.usvm.instrumentation.rd

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.toType
import org.usvm.instrumentation.collector.trace.MockCollector
import org.usvm.instrumentation.instrumentation.JcInstructionTracer
import org.usvm.instrumentation.mock.MockHelper
import org.usvm.instrumentation.testcase.api.*
import org.usvm.instrumentation.testcase.descriptor.StaticDescriptorsBuilder
import org.usvm.instrumentation.testcase.descriptor.UTestExceptionDescriptor
import org.usvm.instrumentation.testcase.descriptor.Value2DescriptorConverter
import org.usvm.instrumentation.testcase.executor.UTestExpressionExecutor
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import org.usvm.instrumentation.util.URLClassPathLoader
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestCall

class UTestExecutorCollectingState(
    jcClasspath: JcClasspath,
    ucp: URLClassPathLoader
) : UTestExecutor(jcClasspath, ucp) {

    override fun buildExecutionState(
        callMethodExpr: UTestCall,
        executor: UTestExpressionExecutor,
        descriptorBuilder: Value2DescriptorConverter,
        accessedStatics: MutableSet<Pair<JcField, JcInstructionTracer.StaticFieldAccessType>>
    ): UTestExecutionState = with(descriptorBuilder) {
        uTestExecutorCache.addAll(executor.objectToInstructionsCache)
        val instanceDescriptor = callMethodExpr.instance?.let {
            buildDescriptorFromUTestExpr(it, executor).getOrNull()
        }
        val argsDescriptors = callMethodExpr.args.map {
            buildDescriptorFromUTestExpr(it, executor).getOrNull()
        }
        val isInit = previousState == null
        val statics = if (isInit) {
            val descriptorsForInitializedStatics =
                staticDescriptorsBuilder.buildDescriptorsForExecutedStatics(accessedStatics, descriptorBuilder)
                    .getOrThrow()
            staticDescriptorsBuilder.builtInitialDescriptors.plus(descriptorsForInitializedStatics)
                .filter { it.value != null }
                .mapValues { it.value!! }
        } else {
            staticDescriptorsBuilder.buildDescriptorsForExecutedStatics(accessedStatics, descriptorBuilder).getOrThrow()
        }
        return UTestExecutionState(instanceDescriptor, argsDescriptors, statics.toMutableMap())
    }
}