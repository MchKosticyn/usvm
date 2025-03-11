package org.usvm.test.api

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcType
import org.usvm.test.internal.SpringMatchersBuilder
import org.usvm.test.internal.SpringRequestBuilder
import org.usvm.test.internal.SpringTestExecBuilder

interface SpringReqAttr

data class ParamAttr(
    val name: String,
    val values: List<Any>,
    val valueType: JcType,
) : SpringReqAttr

data class HeaderAttr(
    val name: String,
    val values: List<Any>,
    val valueType: JcType,
) : SpringReqAttr

data class SpringReqPath(
    val path: String,
    val pathVariables: List<Any>
)

enum class SpringReqKind {
    GET,
    PUT,
    POST,
    PATCH,
    DELETE,
    NONE;

    override fun toString(): String {
        return when (this) {
            GET -> "get"
            PUT -> "put"
            POST -> "post"
            PATCH -> "patch"
            DELETE -> "delete"
            NONE -> "none"
        }
    }

    companion object {
        fun fromString(str: String): SpringReqKind =
            when (str) {
                GET.toString() -> GET
                PUT.toString() -> PUT
                POST.toString() -> POST
                PATCH.toString() -> PATCH
                DELETE.toString() -> DELETE
                NONE.toString() -> NONE
                else -> throw IllegalArgumentException("Unsupported kind: $str")
            }
    }
}

data class SpringResponse(
    val statusCode: Int,
)

class SpringException

class JcSpringTestBuilder {
    private var reqAttrs: MutableList<SpringReqAttr> = mutableListOf()
    private var reqKind: SpringReqKind = SpringReqKind.NONE
    private var reqPath: SpringReqPath = SpringReqPath("", emptyList())
    private var response: SpringResponse? = null
    private var exception: SpringException? = null
    private var generatedTestClass: JcClassType? = null

    fun withReqAttrs(attrs: List<SpringReqAttr>) = apply { this.reqAttrs = attrs.toMutableList() }
    fun addReqAttr(attr: SpringReqAttr) = apply { this.reqAttrs.add(attr) }
    fun withReqKind(kind: SpringReqKind) = apply { this.reqKind = kind }
    fun withReqPath(path: SpringReqPath) = apply { this.reqPath = path }
    fun withResponse(response: SpringResponse?) = apply { this.response = response }
    fun withException(exception: SpringException?) = apply { this.exception = exception }
    fun withGeneratedTestClass(testClass: JcClassType) = apply { this.generatedTestClass = testClass }

    fun build(cp: JcClasspath, forReproducing: Boolean = false): JcSpringTest {
        return when {
            reqKind == SpringReqKind.NONE -> error("request kind not set")

            forReproducing && generatedTestClass == null -> error("generatedTestClass is required for reproducing")

            response != null -> JcSpringResponseTest(
                cp = cp,
                generatedTestClass = generatedTestClass,
                reqAttrs = reqAttrs,
                reqKind = reqKind,
                reqPath = reqPath,
                response = response!!,
            )

            exception != null -> JcSpringExceptionTest(
                cp = cp,
                generatedTestClass = generatedTestClass,
                reqAttrs = reqAttrs,
                reqKind = reqKind,
                reqPath = reqPath,
                exception = exception!!,
            )

            else -> error("test should expect some behaviour")
        }
    }
}

abstract class JcSpringTest(
    val cp: JcClasspath,
    val generatedTestClass: JcClassType?,
    val reqAttrs: List<SpringReqAttr>,
    val reqKind: SpringReqKind,
    val reqPath: SpringReqPath,
) {
    open fun generateTestDSL(): UTest {
        val initStatements: MutableList<UTestInst> = mutableListOf()
        val testExecBuilder = SpringTestExecBuilder.intiTestCtx(
            cp = cp,
            generatedTestClass = generatedTestClass,
        )
        initStatements.addAll(testExecBuilder.getInitDSL())

        val (reqDSL, reqInitDSL) = generateRequestDSL(reqKind, reqPath, reqAttrs)
        initStatements.addAll(reqInitDSL)

        testExecBuilder.addPerformCall(reqDSL)

        val (matchersDSL, matchersInitDSL) = generateMatchersDSL()
        initStatements.addAll(matchersInitDSL)

        matchersDSL.forEach { testExecBuilder.addAndExpectCall(listOf(it)) }

        return UTest(initStatements = initStatements, callMethodExpression = testExecBuilder.getExecDSL())
    }

    protected open fun generateMatchersDSL(): Pair<List<UTestExpression>, List<UTestInst>> =
        error("cannot generate dsl from base")

    protected open fun generateRequestDSL(
        reqKind: SpringReqKind, reqPath: SpringReqPath, reqAttrs: List<SpringReqAttr>
    ): Pair<UTestExpression, List<UTestInst>> = error("cannot generate dsl from base")
}

class JcSpringResponseTest(
    cp: JcClasspath,
    generatedTestClass: JcClassType?,
    reqAttrs: List<SpringReqAttr>,
    reqKind: SpringReqKind,
    reqPath: SpringReqPath,
    val response: SpringResponse
) : JcSpringTest(cp, generatedTestClass, reqAttrs, reqKind, reqPath) {

    override fun generateMatchersDSL(): Pair<List<UTestExpression>, List<UTestInst>> {
        val matchersBuilder = SpringMatchersBuilder(cp)
        matchersBuilder.addStatusCheck(response.statusCode)
        return Pair(matchersBuilder.getMatchersDSL(), matchersBuilder.getInitDSL())
    }

    override fun generateRequestDSL(
        reqKind: SpringReqKind,
        reqPath: SpringReqPath,
        reqAttrs: List<SpringReqAttr>
    ): Pair<UTestExpression, List<UTestInst>> {
        val builder = SpringRequestBuilder.createReq(cp, reqKind, reqPath).addAttrs(reqAttrs)
        return Pair(builder.getDSL(), builder.getInitDSL())
    }
}

class JcSpringExceptionTest(
    cp: JcClasspath,
    generatedTestClass: JcClassType?,
    reqAttrs: List<SpringReqAttr>,
    reqKind: SpringReqKind,
    reqPath: SpringReqPath,
    val exception: SpringException
) : JcSpringTest(cp, generatedTestClass, reqAttrs, reqKind, reqPath)