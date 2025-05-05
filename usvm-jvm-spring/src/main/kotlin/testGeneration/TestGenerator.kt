package testGeneration

import machine.state.JcSpringState
import machine.state.pinnedValues.JcPinnedKey
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.usvm.jvm.util.toTypedMethod
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestMockObject
import org.usvm.test.api.spring.JcMockBean
import org.usvm.test.api.spring.JcSpringRequest
import org.usvm.test.api.spring.JcSpringResponse
import org.usvm.test.api.spring.JcSpringTestBuilder
import org.usvm.test.api.spring.ResolvedSpringException
import org.usvm.test.api.spring.SpringException
import org.usvm.test.api.spring.UTString
import org.usvm.test.api.spring.UnhandledSpringException

private fun JcSpringState.hasResponse(): Boolean {
    return pinnedValues.getValue(JcPinnedKey.responseStatus()) != null
}

private fun JcSpringState.hasResolvedException(): Boolean {
    return pinnedValues.getValue(JcPinnedKey.resolvedExceptionClass()) != null
}

private fun JcSpringState.hasUnhandledException(): Boolean {
    return pinnedValues.getValue(JcPinnedKey.unhandledExceptionClass()) != null
}

private fun JcSpringState.hasException(): Boolean {
    return hasUnhandledException() || hasResolvedException()
}

fun JcSpringState.canGenerateTest(): Boolean {
    return hasResponse() || hasException()
}

data class SpringTestInfo(
    val method: JcMethod,
    val isExceptional: Boolean,
    val test: UTest,
)

internal fun JcSpringState.generateTest(): SpringTestInfo {
    val model = models.first()
    val exprResolver = JcSpringTestExprResolver(ctx, model, memory, entrypoint.toTypedMethod)
    val request = getSpringRequest(this, exprResolver)

    val mocks = getSpringMocks(this, exprResolver)
    val testClass = getGeneratedTestClass(ctx.cp)

    var jcSpringTest = JcSpringTestBuilder(request)
        .withMocks(mocks)
        .withGeneratedTestClass(testClass)

    if (hasException()) {
        val exception = getSpringException(this, exprResolver)
        jcSpringTest = jcSpringTest.withException(exception)
    }
    else {
        val response = getSpringResponse(this, exprResolver)
        jcSpringTest = jcSpringTest.withResponse(response)
    }

    val uTest = jcSpringTest.build(ctx.cp)
        .generateTestDSL { exprResolver.getInstructions() }

    val reqPath = pinnedValues.getValue(JcPinnedKey.requestPath())
        ?: error("Request path is not found in pinned values")
    val pathString = (exprResolver.resolvePinnedValue(reqPath) as UTString).value
    val reqMethod = pinnedValues.getValue(JcPinnedKey.requestMethod())
        ?: error("Request method is not found in pinned values")
    val methodString = (exprResolver.resolvePinnedValue(reqMethod) as UTString).value
    val method = handlerData.find {
        it.pathTemplate == pathString && it.allowedMethods.contains(methodString)
    }?.handler
    check(method != null) { "Could not infer handler method of path" }

    return SpringTestInfo(method, isExceptional, uTest)
}

private fun getSpringException(
    state: JcSpringState,
    exprResolver: JcSpringTestExprResolver
): SpringException {
    if (state.hasUnhandledException()) {
        val exceptionClass = state.pinnedValues
            .getValue(JcPinnedKey.unhandledExceptionClass())!!
            .let { exprResolver.resolvePinnedValue(it) }

        return UnhandledSpringException(exceptionClass)
    }

    val exceptionClass = state.pinnedValues
        .getValue(JcPinnedKey.resolvedExceptionClass())!!
        .let { exprResolver.resolvePinnedValue(it) }

    val exceptionMessage = state.pinnedValues
        .getValue(JcPinnedKey.resolvedExceptionMessage())
        ?.let { exprResolver.resolvePinnedValue(it) }

    return ResolvedSpringException(
        exceptionClass,
        exceptionMessage
    )
}

private fun getGeneratedTestClass(cp: JcClasspath): JcClassOrInterface {
    val testClassName = System.getProperty("generatedTestClass")
    check(testClassName.isNotEmpty()) { "Generated test class name must not be empty" }
    val cl = cp.findClassOrNull(testClassName)
    check(cl != null && cl !is JcUnknownClass)
    return cl
}

private fun getSpringResponse(
    state: JcSpringState,
    exprResolver: JcSpringTestExprResolver
): JcSpringResponse {
    return JcSpringPinnedValuesResponse(state.pinnedValues, exprResolver)
}

fun getSpringMocks(
    state: JcSpringState,
    exprResolver: JcSpringTestExprResolver
): List<JcMockBean> {
    // TODO: Also fields #AA
    val mocks = state.mockedMethodCalls.getMap()
    val distinctMocks = mocks.entries.map { it.key.enclosingClass }.distinct()
    val testMockObjects = distinctMocks.map { type ->
        val distinctMethods = mocks.entries
            .filter { it.key.enclosingClass == type }
            .associate { it.key to it.value.map { v -> exprResolver.resolvePinnedValue(v) } }
        UTestMockObject(
            type.toType(),
            mapOf(),
            distinctMethods
        )
    }

    return testMockObjects.map { JcMockBean(it) }
}

private fun getSpringRequest(
    state: JcSpringState,
    exprResolver: JcSpringTestExprResolver
): JcSpringRequest {
    return JcSpringPinnedValuesRequest(state.pinnedValues, exprResolver)
}
