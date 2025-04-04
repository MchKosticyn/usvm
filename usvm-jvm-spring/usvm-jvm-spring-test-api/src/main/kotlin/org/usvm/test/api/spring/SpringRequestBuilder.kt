package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.int
import org.usvm.jvm.util.stringType
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestCreateArrayExpression
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestIntExpression
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestStaticMethodCall
import org.usvm.test.api.UTestStringExpression

class SpringRequestBuilder private constructor(
    private val initStatements: MutableList<UTestInst>,
    private var reqDSL: UTestExpression,
    private val cp: JcClasspath
) {
    companion object {
        fun createRequest(
            cp: JcClasspath,
            method: JcSpringRequestMethod,
            path: UTString,
            pathVariables: List<Any?>
        ): SpringRequestBuilder =
            commonReqDSLBuilder(cp, method, path, pathVariables)

        private const val MOCK_MVC_REQUEST_BUILDERS_CLASS =
            "org.springframework.test.web.servlet.request.MockMvcRequestBuilders"

        private const val MOCK_HTTP_SERVLET_REQUEST_BUILDER_CLASS =
            "org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder"

        private fun commonReqDSLBuilder(
            cp: JcClasspath,
            method: JcSpringRequestMethod,
            path: UTString,
            pathVariables: List<Any?>
        ): SpringRequestBuilder {
            val requestMethodName = method.name.lowercase()
            val staticMethod = cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CLASS, requestMethodName)
            val initDSL = mutableListOf<UTestInst>()
            val pathArgs = pathVariables.map { it }
            val pathArgsArray = UTestCreateArrayExpression(cp.stringType, UTestIntExpression(pathArgs.size, cp.int))
            val pathArgsInitializer = List(pathArgs.size) {
                UTestArraySetStatement(
                    pathArgsArray,
                    UTestIntExpression(it, cp.int),
                    UTestStringExpression(pathArgs[it].toString(), cp.stringType)
                )
            }
            initDSL.addAll(pathArgsInitializer)
            val argsDSL = mutableListOf<UTestExpression>()
            argsDSL.add(path)
            argsDSL.add(pathArgsArray)
            return SpringRequestBuilder(
                initStatements = initDSL,
                reqDSL = UTestStaticMethodCall(staticMethod, argsDSL),
                cp = cp
            )
        }
    }

    fun getInitDSL(): List<UTestInst> = initStatements

    fun getDSL() = reqDSL

    fun addParameter(parameter: JcSpringHttpParameter): SpringRequestBuilder {
        val method = cp.findJcMethod(MOCK_HTTP_SERVLET_REQUEST_BUILDER_CLASS, "param")
        addMethodCall(method, parameter.getName(), parameter.getValues())
        return this
    }

    fun addHeader(header: JcSpringHttpHeader): SpringRequestBuilder {
        val method = cp.findJcMethod(MOCK_HTTP_SERVLET_REQUEST_BUILDER_CLASS, "header")
        addMethodCall(method, header.getName(), header.getValues())
        return this
    }

    fun addContent(content: UTAny): SpringRequestBuilder {
        val method = cp.findJcMethod(MOCK_HTTP_SERVLET_REQUEST_BUILDER_CLASS, "content", listOf("java.lang.String"))
        reqDSL = UTestMethodCall(
            instance = reqDSL,
            method = method,
            args = listOf(content),
        )
        return this
    }

    private fun addMethodCall(method: JcMethod, str: UTString, arguments: UTStringArray) {
        reqDSL = UTestMethodCall(
            instance = reqDSL,
            method = method,
            args = listOf(str, arguments),
        )
    }
}
