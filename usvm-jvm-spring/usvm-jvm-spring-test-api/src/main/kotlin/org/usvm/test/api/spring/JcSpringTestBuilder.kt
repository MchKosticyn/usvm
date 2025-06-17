package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMockObject

abstract class JcSpringTestBuilder {

    protected val cp: JcClasspath
    private val controller: JcClassOrInterface
    protected val testKind: JcSpringTestKind
    private val testClass: JcClassOrInterface
    protected val decoderApi: JcSpringTestExecutorDecoderApi

    constructor(
        cp: JcClasspath,
        controller: JcClassOrInterface,
        testKind: JcSpringTestKind,
        decoderApi: JcSpringTestExecutorDecoderApi,
        testClass: JcClassOrInterface,
    ) {
        this.cp = cp
        this.controller = controller
        ensureInitializedTestKind(testKind)
        this.testKind = testKind
        this.decoderApi = decoderApi
        this.testClass = testClass
    }

    constructor(
        cp: JcClasspath,
        controller: JcClassOrInterface,
        testKind: JcSpringTestKind,
        decoderApi: JcSpringTestExecutorDecoderApi,
        testClassName: String? = null,
    ) {
        this.cp = cp
        this.controller = controller
        ensureInitializedTestKind(testKind)
        this.testKind = testKind
        this.decoderApi = decoderApi
        this.testClass = findTestClass(cp, testClassName)
    }

    private fun ensureInitializedTestKind(testKind: JcSpringTestKind) {
        when (testKind) {
            is WebMvcTest -> testKind.ensureInitialized(controller)
            else -> Unit
        }
    }

    private fun makeTestClassName(): String {
        return "${controller.name}Tests"
    }

    private fun findTestClass(cp: JcClasspath, name: String?): JcClassOrInterface {
        val testClassesFeature = getSpringTestClassesFeatureIn(cp)

        val testClassName = name ?: makeTestClassName()
        testClassesFeature.testClassFor(testClassName, controller, testKind)

        return cp.findClassOrNull(testClassName) ?: error("test class not found")
    }

    abstract fun buildJcSpringRequest(): JcSpringRequest

    abstract fun buildJcSpringResponse(): JcSpringResponse?

    abstract fun buildSpringException(): SpringException?

    abstract fun buildTableEntities(): List<JcTableEntities>

    abstract fun buildMockBeans(): List<UTestMockObject>

    fun build(): UTest {
        val testExecBuilder = SpringTestExecBuilder.initTestCtx(cp, testClass)
        decoderApi.addInstructions(testExecBuilder.getInitDSL())

        if (testKind.shouldInitTables) {
            val tableEntities = buildTableEntities()
            val tableInitialization = generateTablesDSL(tableEntities, testExecBuilder.testClassExpr)
            decoderApi.addInstructions(tableInitialization)
        }

        val mockBeans = buildMockBeans()
        val mocks = generateMocksDSL(mockBeans, testExecBuilder.testClassExpr)
        decoderApi.addInstructions(mocks)

        val request = buildJcSpringRequest()
        val (requestExpression, requestInitStatements) = generateRequestDSL(request)
        decoderApi.addInstructions(requestInitStatements)
        testExecBuilder.addPerformCall(requestExpression)

        val matchersInitDSL = generateMatchersDSL(testExecBuilder)
        decoderApi.addInstructions(matchersInitDSL)

        testExecBuilder.tryAddAndIgnoreCall()
        return UTest(
            initStatements = decoderApi.initializerInstructions(),
            callMethodExpression = testExecBuilder.getExecDSL()
        )
    }

    private fun generateResponseMatchers(
        response: JcSpringResponse,
        testExecBuilder: SpringTestExecBuilder
    ): List<UTestInst> {
        val matchersBuilder = SpringMatchersBuilder(cp, testExecBuilder)

        matchersBuilder.addStatusCheck(response.getStatus())
        matchersBuilder.addHeadersCheck(response.getHeaders())

        val view = response.getViewName()
        if (view == null) {
            response.getContent()?.let { matchersBuilder.addContentCheck(it) }
        } else {
            matchersBuilder.addViewCheck(view)
        }

        return matchersBuilder.getInitDSL()
    }

    private fun generateExceptionMatchers(
        exception: SpringException,
        testExecBuilder: SpringTestExecBuilder
    ): List<UTestInst> {
        val matchersBuilder = SpringExceptionMatchersBuilder(cp, testExecBuilder)

        if (exception is UnhandledSpringException) {
            matchersBuilder.addUnhandedExceptionCheck(exception.clazz)
        }

        if (exception is ResolvedSpringException) {
            testExecBuilder.addAndReturnCall()
            matchersBuilder.addResolvedExceptionTypeCheck(exception.clazz)
            exception.message?.let{ matchersBuilder.addResolvedExceptionMessageCheck(it) }
        }

        return matchersBuilder.getInitDSL()
    }

    private fun generateMatchersDSL(testExecBuilder: SpringTestExecBuilder): List<UTestInst> {
        val response = buildJcSpringResponse()
        val exception = buildSpringException()
        return if (response != null) {
            generateResponseMatchers(response, testExecBuilder)
        } else if (exception != null) {
            generateExceptionMatchers(exception, testExecBuilder)
        } else {
            emptyList()
        }
    }

    private fun generateRequestDSL(request: JcSpringRequest): Pair<UTestExpression, List<UTestInst>> {
        val builder = SpringRequestBuilder.createRequest(cp, request.getMethod(), request.getPath(), request.getUriVariables())
        request.getParameters().forEach { builder.addParameter(it) }
        request.getHeaders().forEach { builder.addHeader(it) }
        request.getContent()?.let { builder.addContent(it) }
        request.getContentTypeName()?.let { builder.addContentType(it) }

        return builder.getDSL() to builder.getInitDSL()
    }

    private fun generateTablesDSL(tables: List<JcTableEntities>, testClass: UTestExpression): List<UTestInst> {
        val builder = SpringTablesBuilder(cp, testClass)
        tables.forEach { builder.addTable(it) }
        return builder.getStatements()
    }

    private fun generateMocksDSL(mocks: List<UTestMockObject>, testClass: UTestExpression): List<UTestInst> {
        val builder = SpringMockBeanBuilder(cp, testClass)
        mocks.forEach { builder.addMockBean(it) }
        return builder.getInitStatements() + builder.getMockitoCalls()
    }
}
