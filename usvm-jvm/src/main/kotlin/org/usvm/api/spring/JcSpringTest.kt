package org.usvm.api.spring

import jakarta.servlet.http.Cookie
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.MethodNotFoundException
import org.usvm.machine.state.JcState

import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findMethodOrNull
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.toType
import org.jacodb.api.jvm.ext.findType
import org.springframework.mock.web.MockServletContext
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.util.JcTestStateResolver.ResolveMode
import org.usvm.machine.JcContext
import org.usvm.machine.state.JcSpringState
import org.usvm.machine.state.concreteMemory.JcConcreteMemory
import org.usvm.machine.state.pinnedValues.JcSpringPinnedValue
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestIntExpression
import org.usvm.test.api.UTestMockObject
import org.usvm.test.api.UTestStatement
import java.nio.charset.Charset


fun JcClasspath.findJcMethod(cName: String, mName: String): JcTypedMethod {
    val method = this.findClass(cName).toType().findMethodOrNull { it.name == mName }
    method?.let { return it }
    throw MethodNotFoundException("$mName not found")
}

// todo:(path for test pipeline) /owners/find

data class SpringReqPath(
    val path: String,
    val pathVariables: List<Any>
)

class SpringExn

class JcSpringTest private constructor(
    val ctx: JcContext,
    val generatedTestClass: JcClassType,
    private val mocks: List<JcMockBean>,
    private val request: JcSpringRequest,
    private val response: JcSpringResponse?,
    private val exception: SpringExn?,
//    todo: exn
) {
    companion object {
        private val REQUEST_MOD = ResolveMode.MODEL
        private val RESPONSE_MOD = ResolveMode.CURRENT

        fun generateFromState(state: JcSpringState): JcSpringTest =
            if (state.getResult() != null)
                generateResponseTest(state)
            else
                generateExnTest(state)

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

        private fun getSpringMocks(state: JcSpringState): List<JcMockBean> {
            val serviceType = state.ctx.cp.findType("org.usvm.spring.benchmarks.service.CoolService") as JcClassType
            val genIntMethod = serviceType.declaredMethods.find { it.name == "getIntValue" }?.method!!
            val returnListMethod = serviceType.declaredMethods.find { it.name == "returnList" }?.method!!
            val returnArrayListMethod = serviceType.declaredMethods.find { it.name == "returnArrayList" }?.method!!
            val versionField = serviceType.declaredFields.find { it.name == "version" }?.field!!
            val results = List(3) { UTestIntExpression(it + 32, state.ctx.cp.int) }
            val otherResults = List(2) { UTestIntExpression(it + 1, state.ctx.cp.int) }
            val fakeMock = JcRealMockBean(UTestMockObject(
                serviceType,
                mapOf(versionField to UTestIntExpression(123, state.ctx.cp.int)),
                mapOf(genIntMethod to results, returnListMethod to results, returnArrayListMethod to otherResults)
            ))
            return listOf(fakeMock)
        }

        private fun getSpringRequest(state: JcSpringState): JcSpringRequest {
            val requestConcretizer = { value: JcSpringPinnedValue -> concretizeSimple(REQUEST_MOD, state, value.getExpr(), value.getType()) }
            return JcSpringPinnedValuesRequest(state.pinnedValues, requestConcretizer)
        }

        private fun concretizeSimple(mode: ResolveMode, state: JcState, expr: UExpr<out USort>, type: JcType) =
            (state.memory as JcConcreteMemory).concretize(state, state.models[0].eval(expr), type, mode)
    }

    val isSuccess get() = response != null
    val isFail get() = exception != null
    val res get() = assert(isSuccess).let { response!! }
    val exn get() = assert(isFail).let { exception!! }

    fun generateTestDSL(): UTest {
        val initStatements: MutableList<UTestInst> = mutableListOf()
        val testExecBuilder = SpringTestExecDSLBuilder.initTestCtx(
            ctx = ctx,
            generatedTestClass = generatedTestClass,
            fromField = generatedTestClass.fields.first { it.name.contains("mockMvc") }.field //TODO: mb error here
        ).also { initStatements.addAll(it.getInitDSL()) }

        val mocks = generateMocksDSL(mocks, testExecBuilder.getTestClassInstance())
        initStatements.addAll(mocks)

        val reqDSL = generateReqDSL(request).let { (reqDSL, reqInitDSL) ->
            initStatements.addAll(reqInitDSL)
            reqDSL
        }
        testExecBuilder.addPerformCall(reqDSL)

        val matchersDSL = generateMatchersDSL().let { (matchersDSL, matchersInitDSL) ->
            initStatements.addAll(matchersInitDSL)
            matchersDSL
        }
        matchersDSL.forEach { testExecBuilder.addAndExpectCall(listOf(it)) }

        return UTest(
            initStatements = initStatements,
            callMethodExpression = testExecBuilder.getIgnoreDsl()
        )
    }

    private fun generateMatchersDSL(): Pair<List<UTestExpression>, List<UTestInst>> {
        val matchersBuilder = SpringMatchersDSLBuilder(ctx)

        matchersBuilder.addStatusCheck(res.getStatusCode())
        matchersBuilder.addContentCheck(res.getContentAsString())
        matchersBuilder.addHeadersCheck(res.getHeaders())
//      TODO("add more matchers")

        return Pair(matchersBuilder.getMatchersDSL(), matchersBuilder.getInitDSL())
    }

    private fun generateReqDSL(
        request: JcSpringRequest
    ): Pair<UTestExpression, List<UTestInst>> {

        val builder = SpringReqDSLBuilder.createRequest(ctx, request.getMethod(), request.getPath(), request.getUriVariables())
        request.getHeaders().forEach { builder.addHeader(it) }
        request.getParameters().forEach { builder.addParameter(it) }

        return Pair(builder.getDSL(), builder.getInitDSL())
    }

    private fun generateMocksDSL(mocks: List<JcMockBean>, testClass: UTestExpression): List<UTestInst>{
        val builder = SpringMockBeanBuilder(ctx.cp, testClass)
        mocks.forEach { builder.addMock(it) }
        return builder.getInitStatements() + builder.getMockitoCalls()
    }

    fun getPath(): String {
        return request.getPath()
    }
}
