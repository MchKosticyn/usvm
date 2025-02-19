package org.usvm.instrumentation.rd

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.toType
import org.usvm.instrumentation.classloader.WorkerClassLoader
import org.usvm.instrumentation.collector.trace.MockCollector
import org.usvm.instrumentation.collector.trace.TraceCollector
import org.usvm.instrumentation.instrumentation.JcInstructionTracer
import org.usvm.instrumentation.mock.MockHelper
import org.usvm.instrumentation.testcase.api.UTestExecutionExceptionResult
import org.usvm.instrumentation.testcase.api.UTestExecutionInitFailedResult
import org.usvm.instrumentation.testcase.api.UTestExecutionResult
import org.usvm.instrumentation.testcase.api.UTestExecutionState
import org.usvm.instrumentation.testcase.api.UTestExecutionSuccessResult
import org.usvm.instrumentation.testcase.descriptor.StaticDescriptorsBuilder
import org.usvm.instrumentation.testcase.descriptor.UTestExceptionDescriptor
import org.usvm.instrumentation.testcase.descriptor.Value2DescriptorConverter
import org.usvm.instrumentation.testcase.executor.UTestExpressionExecutor
import org.usvm.instrumentation.util.InstrumentationModuleConstants
import org.usvm.instrumentation.util.URLClassPathLoader
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestCall

abstract class UTestExecutor(
    protected val jcClasspath: JcClasspath,
    protected val ucp: URLClassPathLoader
) {

    fun executeUTest(uTest: UTest): UTestExecutionResult {
        when (InstrumentationModuleConstants.testExecutorStaticsRollbackStrategy) {
            StaticsRollbackStrategy.HARD -> workerClassLoader = createWorkerClassLoader()
            else -> {}
        }
        reset()
        val accessedStatics = mutableSetOf<Pair<JcField, JcInstructionTracer.StaticFieldAccessType>>()
        val callMethodExpr = uTest.callMethodExpression

        val executor = UTestExpressionExecutor(workerClassLoader, accessedStatics, mockHelper)
        val initStmts = (uTest.initStatements + listOf(callMethodExpr.instance) + callMethodExpr.args).filterNotNull()
        executor.executeUTestInsts(initStmts)
            ?.onFailure {
                return UTestExecutionInitFailedResult(
                    cause = buildExceptionDescriptor(
                        builder = initStateDescriptorBuilder,
                        exception = it,
                        raisedByUserCode = false
                    ),
                    trace = JcInstructionTracer.getTrace().trace
                )
            }
        accessedStatics.addAll(JcInstructionTracer.getTrace().statics.toSet())

        val initExecutionState = buildExecutionState(
            callMethodExpr = callMethodExpr,
            executor = executor,
            descriptorBuilder = initStateDescriptorBuilder,
            accessedStatics = accessedStatics
        )

        val methodInvocationResult =
            executor.executeUTestInst(callMethodExpr)
        val resultStateDescriptorBuilder =
            Value2DescriptorConverter(workerClassLoader, initStateDescriptorBuilder)
        val unpackedInvocationResult =
            when {
                methodInvocationResult.isFailure -> methodInvocationResult.exceptionOrNull()
                else -> methodInvocationResult.getOrNull()
            }

        val trace = JcInstructionTracer.getTrace()
        accessedStatics.addAll(trace.statics.toSet())

        if (unpackedInvocationResult is Throwable) {
            val resultExecutionState =
                buildExecutionState(callMethodExpr, executor, resultStateDescriptorBuilder, accessedStatics)
            return UTestExecutionExceptionResult(
                cause = buildExceptionDescriptor(
                    builder = resultStateDescriptorBuilder,
                    exception = unpackedInvocationResult,
                    raisedByUserCode = methodInvocationResult.isSuccess
                ),
                trace = JcInstructionTracer.getTrace().trace,
                initialState = initExecutionState,
                resultState = resultExecutionState
            )
        }

        val methodInvocationResultDescriptor =
            resultStateDescriptorBuilder.buildDescriptorResultFromAny(unpackedInvocationResult, callMethodExpr.type)
                .getOrNull()
        val resultExecutionState =
            buildExecutionState(callMethodExpr, executor, resultStateDescriptorBuilder, accessedStatics)

        val accessedStaticsFields = accessedStatics.map { it.first }
        val staticsToRemoveFromInitState = initExecutionState.statics.keys.filter { it !in accessedStaticsFields }
        staticsToRemoveFromInitState.forEach { initExecutionState.statics.remove(it) }
        if (InstrumentationModuleConstants.testExecutorStaticsRollbackStrategy == StaticsRollbackStrategy.ROLLBACK) {
            staticDescriptorsBuilder.rollBackStatics()
        } else if (InstrumentationModuleConstants.testExecutorStaticsRollbackStrategy == StaticsRollbackStrategy.REINIT) {
            workerClassLoader.reset(accessedStaticsFields)
        }


        return UTestExecutionSuccessResult(
            trace.trace, methodInvocationResultDescriptor, initExecutionState, resultExecutionState
        )
    }

    abstract protected fun buildExecutionState(
        callMethodExpr: UTestCall,
        executor: UTestExpressionExecutor,
        descriptorBuilder: Value2DescriptorConverter,
        accessedStatics: MutableSet<Pair<JcField, JcInstructionTracer.StaticFieldAccessType>>
    ): UTestExecutionState

    protected fun createWorkerClassLoader() =
        WorkerClassLoader(
            urlClassPath = ucp,
            traceCollectorClassLoader = this::class.java.classLoader,
            traceCollectorClassName = TraceCollector::class.java.name,
            mockCollectorClassName = MockCollector::class.java.name,
            jcClasspath = jcClasspath
        )

    protected var workerClassLoader = createWorkerClassLoader()
    protected var initStateDescriptorBuilder = Value2DescriptorConverter(
        workerClassLoader = workerClassLoader,
        previousState = null
    )
    protected var staticDescriptorsBuilder = StaticDescriptorsBuilder(
        workerClassLoader = workerClassLoader,
        initialValue2DescriptorConverter = initStateDescriptorBuilder
    )
    protected var mockHelper = MockHelper(
        jcClasspath = jcClasspath,
        classLoader = workerClassLoader
    )

    init {
        workerClassLoader.setStaticDescriptorsBuilder(staticDescriptorsBuilder)
    }

    protected fun reset() {
        initStateDescriptorBuilder = Value2DescriptorConverter(
            workerClassLoader = workerClassLoader,
            previousState = null
        )
        staticDescriptorsBuilder.setClassLoader(workerClassLoader)
        staticDescriptorsBuilder.setInitialValue2DescriptorConverter(initStateDescriptorBuilder)
        //In case of new worker classloader
        workerClassLoader.setStaticDescriptorsBuilder(staticDescriptorsBuilder)
        JcInstructionTracer.reset()
        MockCollector.mocks.clear()
    }

    protected fun buildExceptionDescriptor(
        builder: Value2DescriptorConverter,
        exception: Throwable,
        raisedByUserCode: Boolean
    ): UTestExceptionDescriptor {
        val descriptor =
            builder.buildDescriptorResultFromAny(any = exception, type = null).getOrNull() as? UTestExceptionDescriptor
        return descriptor
            ?.also { it.raisedByUserCode = raisedByUserCode }
            ?: UTestExceptionDescriptor(
                type = jcClasspath.findClassOrNull(exception::class.java.name)?.toType()
                    ?: jcClasspath.findClass<Exception>().toType(),
                message = exception.message ?: "message_is_null",
                stackTrace = listOf(),
                raisedByUserCode = raisedByUserCode
            )
    }
}