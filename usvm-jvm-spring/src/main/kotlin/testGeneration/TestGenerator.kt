package testGeneration

import machine.JcSpringAnalysisMode
import machine.state.JcSpringState
import machine.state.pinnedValues.JcPinnedKey
import machine.state.pinnedValues.JcSpringMockedCalls
import machine.state.tableContent
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.usvm.jvm.util.toTypedMethod
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestClassExpression
import org.usvm.test.api.UTestMockObject
import org.usvm.test.api.UTestNullExpression
import org.usvm.test.api.spring.JcSpringRequest
import org.usvm.test.api.spring.JcSpringResponse
import org.usvm.test.api.spring.JcSpringTestBuilder
import org.usvm.test.api.spring.JcSpringTestKind
import org.usvm.test.api.spring.JcTableEntities
import org.usvm.test.api.spring.ResolvedSpringException
import org.usvm.test.api.spring.SpringBootTest
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

private class JcStateSpringTestBuilder(
    cp: JcClasspath,
    controller: JcClassOrInterface,
    testKind: JcSpringTestKind,
    testClass: JcClassOrInterface,
    private val state: JcSpringState,
    private val resolver: JcSpringTestExprResolver
): JcSpringTestBuilder(
    cp,
    controller,
    testKind,
    resolver.decoderApi,
    testClass
) {
    override fun buildJcSpringRequest(): JcSpringRequest {
        return getSpringRequest(state, resolver)
    }

    override fun buildJcSpringResponse(): JcSpringResponse? {
        if (!state.hasResponse())
            return null

        return getSpringResponse(state, resolver)
    }

    override fun buildSpringException(): SpringException? {
        if (!state.hasException())
            return null

        return getSpringException(state, resolver)
    }

    override fun buildTableEntities(): List<JcTableEntities> {
        return getSpringTables(state.tableEntities, resolver)
    }

    override fun buildMockBeans(): List<UTestMockObject> {
        return getSpringMocks(state.mockedMethodCalls, resolver)
    }
}

private fun JcSpringState.createSpringTestKind(testClass: JcClassOrInterface): JcSpringTestKind {
    return when (springAnalysisMode) {
        JcSpringAnalysisMode.SpringBootTest -> {
            val testAnnotation = testClass.annotations.find {
                it.name == "org.springframework.boot.test.context.SpringBootTest"
            } ?: error("SpringBootTest annotation not found")
            val annotationValues = testAnnotation.values["classes"] as List<*>
            val applicationClass = annotationValues.single() as JcClassOrInterface
            SpringBootTest(applicationClass)
        }
        JcSpringAnalysisMode.SpringJpaTest -> TODO("not implemented")
    }
}

internal fun JcSpringState.generateTest(): SpringTestInfo {
    val model = springMemory.getFixedModel(this)
    val resolver = JcSpringTestExprResolver(ctx, model, memory, entrypoint.toTypedMethod)

    val reqPath = pinnedValues.getValue(JcPinnedKey.requestPath())
        ?: error("Request path is not found in pinned values")
    val pathString = (resolver.resolvePinnedValue(reqPath) as UTString).value
    val reqMethod = pinnedValues.getValue(JcPinnedKey.requestMethod())
        ?: error("Request method is not found in pinned values")
    val methodString = (resolver.resolvePinnedValue(reqMethod) as UTString).value
    val handler = handlerData.find {
        it.pathTemplate == pathString && it.allowedMethods.contains(methodString)
    }?.handler

    check(handler != null) { "Could not infer handler method of path" }

    val controller = handler.enclosingClass
    val testClass = getGeneratedTestClass(ctx.cp)

    val testKind = createSpringTestKind(testClass)
    val testBuilder = JcStateSpringTestBuilder(ctx.cp, controller, testKind, testClass, this, resolver)
    val uTest = testBuilder.build()
    return SpringTestInfo(handler, isExceptional, uTest)
}

private fun getSpringException(
    state: JcSpringState,
    exprResolver: JcSpringTestExprResolver
): SpringException {
    if (state.hasUnhandledException()) {
        val exceptionClass = state.pinnedValues
            .getValue(JcPinnedKey.unhandledExceptionClass())!!
            .let { exprResolver.resolvePinnedValue(it) }

        return UnhandledSpringException(
            exceptionClass as UTestClassExpression
        )
    }

    val exceptionClass = state.pinnedValues
        .getValue(JcPinnedKey.resolvedExceptionClass())!!
        .let { exprResolver.resolvePinnedValue(it) }

    val exceptionMessage = state.pinnedValues
        .getValue(JcPinnedKey.resolvedExceptionMessage())
        ?.let { exprResolver.resolvePinnedValue(it) }

    return ResolvedSpringException(
        exceptionClass as UTestClassExpression,
        exceptionMessage as UTString?
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

private fun getSpringMocks(
    mockedCalls: JcSpringMockedCalls,
    exprResolver: JcSpringTestExprResolver
): List<UTestMockObject> {
    // TODO: Support fields #AA
    val mocks = mockedCalls.getMap()
    val distinctMocks = mocks.entries.map { it.key.enclosingClass }.distinct()
    return distinctMocks.map { type ->
        val distinctMethods = mocks.entries
            .filter { it.key.enclosingClass == type }
            .associate { it.key to it.value.map { v -> exprResolver.resolvePinnedValue(v) } }
        UTestMockObject(
            type.toType(),
            mapOf(),
            distinctMethods
        )
    }
}

private fun getSpringTables(
    tables: Map<String, tableContent>,
    exprResolver: JcSpringTestExprResolver
): List<JcTableEntities> {
    return tables.mapNotNull { (tableName, entitiesWithType) ->
        val (entities, type) = entitiesWithType
        if (entities.isEmpty()) return@mapNotNull null
        val resolvedIndexedEntities = entities.map { (entity, index) ->
            val resolved = exprResolver.resolveExpr(entity, type)
            check(resolved !is UTestNullExpression)
            resolved to index
        }
        val sortedEntities = resolvedIndexedEntities.sortedBy { it.second }.map { it.first }
        JcTableEntities(tableName, sortedEntities)
    }
}

private fun getSpringRequest(
    state: JcSpringState,
    exprResolver: JcSpringTestExprResolver
): JcSpringRequest {
    return JcSpringPinnedValuesRequest(state.pinnedValues, exprResolver)
}
