package org.usvm.test.internal

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.int
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestCreateArrayExpression
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestIntExpression
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestStaticMethodCall
import org.usvm.test.api.UTestStringExpression
import org.usvm.test.api.spring.JcSpringHttpHeader
import org.usvm.test.api.spring.JcSpringHttpParameter
import org.usvm.test.api.spring.JcSpringRequestMethod
import org.usvm.test.api.stringType

class SpringRequestBuilder private constructor(
    private val initStatements: MutableList<UTestInst>,
    private var reqDSL: UTestExpression,
    private val cp: JcClasspath
) {
    companion object {
        fun createRequest(
            cp: JcClasspath,
            method: JcSpringRequestMethod,
            path: String,
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
            path: String,
            pathVariables: List<Any?>
        ): SpringRequestBuilder {
            val requestMethodName = method.name.lowercase()
            val staticMethod = cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CLASS, requestMethodName).method
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
            argsDSL.add(UTestStringExpression(path, cp.stringType))
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

    fun addParameter(attr: JcSpringHttpParameter): SpringRequestBuilder {
        val method = cp.findJcMethod(MOCK_HTTP_SERVLET_REQUEST_BUILDER_CLASS, "param").method
        addStrArrOfStrCallDSL(method, attr.getName(), attr.getValues())
        return this
    }

    fun addHeader(attr: JcSpringHttpHeader): SpringRequestBuilder {
        val method = cp.findJcMethod(MOCK_HTTP_SERVLET_REQUEST_BUILDER_CLASS, "header").method
        addStrArrOfStrCallDSL(method, attr.getName(), attr.getValues())
        return this
    }

    fun addContent(content: String): SpringRequestBuilder {
        val method = cp.findJcMethod(MOCK_HTTP_SERVLET_REQUEST_BUILDER_CLASS, "content", listOf("java.lang.String")).method
        reqDSL = UTestMethodCall(
            instance = reqDSL,
            method = method,
            args = listOf(UTestStringExpression(content, cp.stringType)),
        )
        return this
    }

    private fun addStrArrOfStrCallDSL(mName: JcMethod, str: String, arrOfStr: List<Any>) {
        val argsDSL = mutableListOf<UTestExpression>()
        val argsArray = UTestCreateArrayExpression(cp.stringType, UTestIntExpression(arrOfStr.size, cp.int))
        val argsInit = List(arrOfStr.size) {
            UTestArraySetStatement(
                argsArray,
                UTestIntExpression(it, cp.int),
                UTestStringExpression(arrOfStr[it].toString(), cp.stringType)
            )
        }
        initStatements.addAll(argsInit)
        argsDSL.add(UTestStringExpression(str, cp.stringType))
        argsDSL.add(argsArray)
        reqDSL = UTestMethodCall(
            instance = reqDSL,
            method = mName,
            args = argsDSL,
        )
    }
}
