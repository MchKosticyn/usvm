package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.usvm.jvm.util.stringType
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestCreateArrayExpression
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestGetStaticFieldExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestIntExpression
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestStaticMethodCall

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
            pathVariables: List<UTAny>
        ): SpringRequestBuilder =
            commonReqDSLBuilder(cp, method, path, pathVariables)

        private const val MOCK_MVC_REQUEST_BUILDERS_CLASS =
            "org.springframework.test.web.servlet.request.MockMvcRequestBuilders"

        private const val MOCK_HTTP_SERVLET_REQUEST_BUILDER_CLASS =
            "org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder"

        private const val SECURITY_MOCK_MVC_REQUEST_POST_PROCESSORS =
            "org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors"

        private fun commonReqDSLBuilder(
            cp: JcClasspath,
            method: JcSpringRequestMethod,
            path: UTString,
            pathVariables: List<UTAny>
        ): SpringRequestBuilder {
            val requestMethodName = method.name.lowercase()
            val staticMethod = cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CLASS, requestMethodName)

            val initDSL = mutableListOf<UTestInst>()

            val pathArgs = pathVariables.map { it }
            val pathArgsArray = UTestCreateArrayExpression(cp.objectType, UTestIntExpression(pathArgs.size, cp.int))
            val pathArgsInitializer = List(pathArgs.size) {
                UTestArraySetStatement(
                    pathArgsArray,
                    UTestIntExpression(it, cp.int),
                    pathArgs[it]
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

    fun addUser(user: UTAny): SpringRequestBuilder {
        val withMethod = cp.findJcMethod(
            MOCK_HTTP_SERVLET_REQUEST_BUILDER_CLASS,
            "with"
        )
        val userMethod = cp.findJcMethod(
            SECURITY_MOCK_MVC_REQUEST_POST_PROCESSORS,
            "user",
            listOf("org.springframework.security.core.userdetails.UserDetails")
        )

        val userRequestPostProcessor = UTestStaticMethodCall(userMethod, listOf(user))
        reqDSL = UTestMethodCall(
            instance = reqDSL,
            method = withMethod,
            args = listOf(userRequestPostProcessor),
        )
        return this
    }

    fun addHeader(header: JcSpringHttpHeader): SpringRequestBuilder {
        val method = cp.findJcMethod(MOCK_HTTP_SERVLET_REQUEST_BUILDER_CLASS, "header")
        addMethodCall(method, header.getName(), header.getValues())
        return this
    }

    fun addContent(content: UTAny): SpringRequestBuilder {
        val method = cp.findJcMethod(
            MOCK_HTTP_SERVLET_REQUEST_BUILDER_CLASS,
            "content",
            listOf("java.lang.String")
        )
        reqDSL = UTestMethodCall(
            instance = reqDSL,
            method = method,
            args = listOf(content),
        )
        return this
    }

    private fun mediaTypeOfName(name: UTString): UTestGetStaticFieldExpression {
        val mediaTypeClass = cp.findClass("org.springframework.http.MediaType")
        val fieldName = name.value
        val field = mediaTypeClass.declaredFields.single { it.name == fieldName }
        check(field.isStatic)
        return UTestGetStaticFieldExpression(field)
    }

    fun addContentType(contentTypeName: UTString): SpringRequestBuilder {
        val method = cp.findJcMethod(
            MOCK_HTTP_SERVLET_REQUEST_BUILDER_CLASS,
            "contentType",
            listOf("org.springframework.http.MediaType")
        )
        reqDSL = UTestMethodCall(
            instance = reqDSL,
            method = method,
            args = listOf(mediaTypeOfName(contentTypeName)),
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
