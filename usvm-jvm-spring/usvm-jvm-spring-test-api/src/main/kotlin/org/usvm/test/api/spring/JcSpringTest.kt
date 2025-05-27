package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestClassExpression
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst

abstract class SpringException

class UnhandledSpringException(
    val clazz: UTestClassExpression
) : SpringException()

class ResolvedSpringException(
    val clazz: UTestClassExpression,
    val message: UTString?
) : SpringException()

class JcSpringTestBuilder(
    private val controller: JcClassOrInterface,
    private val request: JcSpringRequest,
    private val testKind: JcSpringTestKind
) {
    private var response: JcSpringResponse? = null
    private var exception: SpringException? = null
    private var generatedTestClass: JcClassOrInterface? = null
    private var generatedTestClassName: String? = null
    private var mocks: List<JcMockBean> = emptyList()
    private var tables: List<JcTableEntities> = emptyList()

    init {
        when (testKind) {
            is WebMvcTest -> testKind.ensureInitialized(controller)
            else -> Unit
        }
    }

    fun withResponse(response: JcSpringResponse) = apply { this.response = response }
    fun withException(exception: SpringException) = apply { this.exception = exception }
    fun withGeneratedTestClass(testClass: JcClassOrInterface) = apply { this.generatedTestClass = testClass }
    fun withGeneratedTestClassName(name: String) = apply { this.generatedTestClassName = name }
    fun withMocks(mocks: List<JcMockBean>) = apply { this.mocks = mocks }
    fun withTables(tables: List<JcTableEntities>) = apply { this.tables = tables }

    private fun makeTestClassName(): String {
        return "${controller.name}Tests"
    }

    private fun findTestClass(cp: JcClasspath): JcClassOrInterface {
        val testClassesFeature = getSpringTestClassesFeatureIn(cp)

        val testClassName = generatedTestClassName ?: makeTestClassName()
        testClassesFeature.testClassFor(testClassName, controller, testKind)

        return cp.findClassOrNull(testClassName) ?: error("test class not found")
    }

    fun build(cp: JcClasspath): JcSpringTest {
        val testClass = generatedTestClass ?: findTestClass(cp)
        return when {
            response != null -> JcSpringResponseTest(
                cp = cp,
                generatedTestClass = testClass,
                mocks = mocks,
                request = request,
                tables = tables,
                response = response!!,
            )

            exception != null -> JcSpringExceptionTest(
                cp = cp,
                generatedTestClass = testClass,
                mocks = mocks,
                request = request,
                tables = tables,
                exception = exception!!,
            )

            else -> JcSpringTest(
                cp = cp,
                generatedTestClass = testClass,
                mocks = mocks,
                request = request,
                tables = tables,
            )
        }
    }
}

open class JcSpringTest internal constructor(
    val cp: JcClasspath,
    private val generatedTestClass: JcClassOrInterface,
    private val mocks: List<JcMockBean>,
    private val request: JcSpringRequest,
    private val tables: List<JcTableEntities>,
) {
    fun generateTestDSL(additionalInstructions: () -> List<UTestInst>): UTest {
        val initStatements: MutableList<UTestInst> = mutableListOf()
        val testExecBuilder = SpringTestExecBuilder.initTestCtx(cp, generatedTestClass)
        initStatements.addAll(testExecBuilder.getInitDSL())

        val tables = generateTablesDSL(tables, testExecBuilder.testClassExpr)
        initStatements.addAll(tables)

        val mocks = generateMocksDSL(mocks, testExecBuilder.testClassExpr)
        initStatements.addAll(mocks)

        val (requestExpression, requestInitStatements) = generateRequestDSL()
        initStatements.addAll(requestInitStatements)

        testExecBuilder.addPerformCall(requestExpression)

        val matchersInitDSL = generateMatchersDSL(testExecBuilder)
        initStatements.addAll(matchersInitDSL)

        initStatements.addAll(additionalInstructions())

        testExecBuilder.tryAddAndIgnoreCall()
        return UTest(initStatements = initStatements, callMethodExpression = testExecBuilder.getExecDSL())
    }

    protected open fun generateMatchersDSL(testExecBuilder: SpringTestExecBuilder): List<UTestInst> =
        emptyList()

    private fun generateRequestDSL(): Pair<UTestExpression, List<UTestInst>> {
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

    private fun generateMocksDSL(mocks: List<JcMockBean>, testClass: UTestExpression): List<UTestInst> {
        val builder = SpringMockBeanBuilder(cp, testClass)
        mocks.forEach { builder.addMock(it) }
        return builder.getInitStatements() + builder.getMockitoCalls()
    }
}

class JcSpringResponseTest internal constructor(
    cp: JcClasspath,
    generatedTestClass: JcClassOrInterface,
    mocks: List<JcMockBean>,
    request: JcSpringRequest,
    tables: List<JcTableEntities>,
    val response: JcSpringResponse
) : JcSpringTest(cp, generatedTestClass, mocks, request, tables) {

    override fun generateMatchersDSL(testExecBuilder: SpringTestExecBuilder): List<UTestInst> {
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
}

class JcSpringExceptionTest internal constructor(
    cp: JcClasspath,
    generatedTestClass: JcClassOrInterface,
    mocks: List<JcMockBean>,
    request: JcSpringRequest,
    tables: List<JcTableEntities>,
    val exception: SpringException
) : JcSpringTest(cp, generatedTestClass, mocks, request, tables) {

    override fun generateMatchersDSL(testExecBuilder: SpringTestExecBuilder): List<UTestInst> {
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
}
