package testGeneration

import machine.state.JcSpringState
import machine.state.pinnedValues.JcSpringPinnedValues
import org.jacodb.api.jvm.JcClasspath
import org.usvm.test.api.UTest
import org.usvm.test.api.spring.JcSpringTest
import org.usvm.test.api.spring.JcSpringTestBuilder
import utils.toTypedMethod

fun JcSpringState.generateTest(): UTest {
    val cp = ctx.cp
    JcSpringTestBuilder()
    if (getResult() != null)
        generateResponseTest(state)
    else
        generateExnTest(state)
}

private fun generateResponseTest(state: JcSpringState): JcSpringTest = JcSpringTest(
    state.ctx,
    getGeneratedClassName(state.ctx.cp),
    mocks = getSpringMocks(state),
    request = getSpringRequest(state),
    response = getSpringResponse(state.ctx.cp, state),
    exception = null
)

private fun generateExnTest(state: JcSpringState): JcSpringTest = JcSpringTest(
    state.ctx,
    getGeneratedClassName(state.ctx.cp),
    mocks = getSpringMocks(state),
    request = getSpringRequest(state),
    response = null,
    exception = getSpringExn(),
)

private fun getSpringExn(): SpringExn {
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

private fun getSpringResponse(cp: JcClasspath, state: JcSpringState): JcSpringResponse {
    // Will be refactored with common refactor merge!!
    val result = state.getResult()
    assert(result != null)
    val expr = result ?: throw IllegalArgumentException("No Response")
    val valueExpr = state.models[0].eval(expr)

    val type = cp.findType("org.springframework.mock.web.MockHttpServletResponse")

    // TODO: problem with cast
    val response = concretizeSimple(
        RESPONSE_MOD,
        state,
        valueExpr,
        type
    )

    check(response != null)
    return JcSpringResponse(response)
}

fun getSpringMocks(
    pinnedValues: JcSpringPinnedValues,
    exprResolver: JcSpringTestExprResolver
): Pair<List<JcMockBean>, List<UTestInst>> {
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
    return testMockObjects.map { JcMockBean(it) } to exprResolver.getInstructions()
}

private fun getSpringMocks(state: JcSpringState): Pair<List<JcMockBean>, List<UTestInst>> {
    val resolver = createExprResolver(state)
    return resolver.withMode(REQUEST_MOD) {
        return@withMode JcMockBean.ofPinnedValues(state.pinnedValues, resolver)
    }
}

private fun getSpringRequest(state: JcSpringState): JcSpringRequest {
    val requestConcretizer = { value: JcSpringPinnedValue -> concretizeSimple(REQUEST_MOD, state, value.getExpr(), value.getType()) }
    return JcSpringPinnedValuesRequest(state.pinnedValues, requestConcretizer)
}

private fun concretizeSimple(mode: ResolveMode, state: JcState, expr: UExpr<out USort>, type: JcType) =
    (state.memory as JcConcreteMemory).concretize(state, state.models[0].eval(expr), type, mode)
