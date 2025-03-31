package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst

class SpringException

class JcSpringTestBuilder(
    private val request: JcSpringRequest,
) {
    private var response: JcSpringResponse? = null
    private var exception: SpringException? = null
    private var generatedTestClass: JcClassType? = null
    private var mocks: MutableList<JcMockBean> = mutableListOf()

    fun withResponse(response: JcSpringResponse) = apply { this.response = response }
    fun withException(exception: SpringException) = apply { this.exception = exception }
    fun withGeneratedTestClass(testClass: JcClassType) = apply { this.generatedTestClass = testClass }
    fun withMocks(mocks: List<JcMockBean>) = apply { this.mocks = mocks.toMutableList() }

    fun build(cp: JcClasspath): JcSpringTest {
        return when {
            response != null -> JcSpringResponseTest(
                cp = cp,
                generatedTestClass = generatedTestClass,
                mocks = mocks,
                request = request,
                response = response!!
            )

            exception != null -> JcSpringExceptionTest(
                cp = cp,
                generatedTestClass = generatedTestClass,
                mocks = mocks,
                request = request,
                exception = exception!!
            )

            else -> JcSpringTest(
                cp = cp,
                generatedTestClass = generatedTestClass,
                mocks = mocks,
                request = request
            )
        }
    }
}

open class JcSpringTest internal constructor(
    val cp: JcClasspath,
    private val generatedTestClass: JcClassType?,
    private val mocks: List<JcMockBean>,
    private val request: JcSpringRequest
) {
    fun generateTestDSL(additionalInstructions: () -> List<UTestInst>): UTest {
        val initStatements: MutableList<UTestInst> = mutableListOf()
        val testExecBuilder = SpringTestExecBuilder.initTestCtx(cp, generatedTestClass)
        initStatements.addAll(testExecBuilder.getInitDSL())

        val mocks = generateMocksDSL(mocks, testExecBuilder.testClassExpr)
        initStatements.addAll(mocks)

        val (reqDSL, reqInitDSL) = generateRequestDSL()
        initStatements.addAll(reqInitDSL)

        testExecBuilder.addPerformCall(reqDSL)

        val (matchersDSL, matchersInitDSL) = generateMatchersDSL()
        initStatements.addAll(matchersInitDSL)

        matchersDSL.forEach { testExecBuilder.addAndExpectCall(listOf(it)) }

        initStatements.addAll(additionalInstructions())
        return UTest(initStatements = initStatements, callMethodExpression = testExecBuilder.getExecDSL())
    }

    protected open fun generateMatchersDSL(): Pair<List<UTestExpression>, List<UTestInst>> =
        emptyList<UTestExpression>() to emptyList()

    private fun generateRequestDSL(): Pair<UTestExpression, List<UTestInst>> {
        val builder = SpringRequestBuilder.createRequest(cp, request.getMethod(), request.getPath(), request.getUriVariables())
        request.getParameters().forEach { builder.addParameter(it) }
        request.getHeaders().forEach { builder.addHeader(it) }
        request.getContent()?.let { builder.addContent(it) }

        return builder.getDSL() to builder.getInitDSL()
    }

    private fun generateMocksDSL(mocks: List<JcMockBean>, testClass: UTestExpression?): List<UTestInst>{
        val builder = SpringMockBeanBuilder(cp, testClass)
        mocks.forEach { builder.addMock(it) }
        return builder.getInitStatements() + builder.getMockitoCalls()
    }
}

class JcSpringResponseTest internal constructor(
    cp: JcClasspath,
    generatedTestClass: JcClassType?,
    mocks: List<JcMockBean>,
    request: JcSpringRequest,
    val response: JcSpringResponse
) : JcSpringTest(cp, generatedTestClass, mocks, request) {

    override fun generateMatchersDSL(): Pair<List<UTestExpression>, List<UTestInst>> {
        val matchersBuilder = SpringMatchersBuilder(cp)
        matchersBuilder.addStatusCheck(response.getStatus())
        response.getContent()?.let { matchersBuilder.addContentCheck(it) }
        matchersBuilder.addHeadersCheck(response.getHeaders())
        return matchersBuilder.getMatchersDSL() to matchersBuilder.getInitDSL()
    }
}

class JcSpringExceptionTest internal constructor(
    cp: JcClasspath,
    generatedTestClass: JcClassType?,
    mocks: List<JcMockBean>,
    request: JcSpringRequest,
    val exception: SpringException
) : JcSpringTest(cp, generatedTestClass, mocks, request) {

    override fun generateMatchersDSL(): Pair<List<UTestExpression>, List<UTestInst>> {
        TODO()
    }
}
