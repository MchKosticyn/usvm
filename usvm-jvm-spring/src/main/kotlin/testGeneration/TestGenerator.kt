package testGeneration

import machine.state.JcSpringState
import machine.state.pinnedValues.JcObjectPinnedKey
import machine.state.pinnedValues.JcPinnedKey
import machine.state.pinnedValues.JcSpringPinnedValueSource
import machine.state.pinnedValues.JcSpringPinnedValues
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.usvm.api.util.JcTestStateResolver
import org.usvm.jvm.util.toTypedMethod
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestMockObject
import org.usvm.test.api.spring.JcMockBean
import org.usvm.test.api.spring.JcSpringRequest
import org.usvm.test.api.spring.JcSpringResponse
import org.usvm.test.api.spring.JcSpringTestBuilder
import org.usvm.test.api.spring.SpringException
import org.usvm.test.api.spring.UTString

fun JcSpringState.canGenerateTest(): Boolean {
    return pinnedValues.getValue(JcPinnedKey.requestPath()) != null
            && pinnedValues.getValue(JcPinnedKey.responseStatus()) != null
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
    val response = getSpringResponse(this, exprResolver)
    val mocks = getSpringMocks(pinnedValues, exprResolver)
    val testClass = getGeneratedTestClass(ctx.cp)

    val jcSpringTest = JcSpringTestBuilder(request)
        .withResponse(response)
        .withMocks(mocks)
        .withGeneratedTestClass(testClass)

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

private fun getSpringExn(): SpringException {
    TODO()
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
    pinnedValues: JcSpringPinnedValues,
    exprResolver: JcSpringTestExprResolver
): List<JcMockBean> {
    // TODO: Also fields #AA
    val mocks = pinnedValues.getValuesOfSource<JcObjectPinnedKey<JcMethod>>(JcSpringPinnedValueSource.MOCK_RESULT)
    val distinctMocks = mocks.entries.mapNotNull { it.key.getObj()?.enclosingClass }.distinct()
    val testMockObjects = distinctMocks.map { type ->
        val distinctMethods = mocks.entries
            .filter { it.key.getObj()?.enclosingClass == type }
            .groupBy({ it.key.getObj()!! }, { exprResolver.resolvePinnedValue(it.value) })
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
