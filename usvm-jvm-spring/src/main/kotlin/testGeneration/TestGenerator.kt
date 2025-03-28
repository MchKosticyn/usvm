package testGeneration

import machine.state.JcSpringState
import machine.state.pinnedValues.JcObjectPinnedKey
import machine.state.pinnedValues.JcSpringPinnedValueSource
import machine.state.pinnedValues.JcSpringPinnedValues
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.toType
import org.usvm.api.util.JcTestStateResolver
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestMockObject
import org.usvm.test.api.spring.JcMockBean
import org.usvm.test.api.spring.JcSpringRequest
import org.usvm.test.api.spring.JcSpringResponse
import org.usvm.test.api.spring.JcSpringTestBuilder
import org.usvm.test.api.spring.SpringException
import utils.toTypedMethod


fun JcSpringState.generateTest(): UTest {
    val exprResolver = createExprResolver(this)
    val request = getSpringRequest(this, exprResolver)
    val response = getSpringResponse(this, exprResolver)
    val mocks = getSpringMocks(pinnedValues, exprResolver)
    val testClass = getGeneratedClassName(ctx.cp)

    val test = JcSpringTestBuilder(request)
        .withResponse(response)
        .withMocks(mocks)
        .withGeneratedTestClass(testClass)
        .withAdditionalInstructions(exprResolver.getInstructions())

    return test
        .build(ctx.cp)
        .generateTestDSL()
}

private fun getSpringExn(): SpringException {
    TODO()
}

private fun createExprResolver(state: JcSpringState): JcSpringTestExprResolver {
    return JcSpringTestExprResolver(
        state.ctx,
        state.models[0],
        state.memory,
        state.entrypoint.toTypedMethod
    )
}

private fun getGeneratedClassName(cp: JcClasspath): JcClassType {
    // TODO hardcoded
    val cl = cp.findClassOrNull("org.usvm.spring.benchmarks.StartSpringTestClass") //TODO: get it from state? (it is generated in runtime)
    check(cl != null)
    return cl.toType()
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
    return exprResolver.withMode(JcTestStateResolver.ResolveMode.MODEL) {
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
        testMockObjects.map { JcMockBean(it) }
    }
}

private fun getSpringRequest(
    state: JcSpringState,
    exprResolver: JcSpringTestExprResolver
): JcSpringRequest {
    return JcSpringPinnedValuesRequest(state.pinnedValues, exprResolver)
}