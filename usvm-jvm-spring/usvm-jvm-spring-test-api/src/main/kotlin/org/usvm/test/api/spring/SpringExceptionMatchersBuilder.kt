package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.findType
import org.usvm.test.api.UTestClassExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestAssertEqualsCall
import org.usvm.test.api.UTestStaticMethodCall

class SpringExceptionMatchersBuilder (
    private val cp: JcClasspath,
    private val testExecBuilder: SpringTestExecBuilder
) {
    private val initStatements: MutableList<UTestInst> = mutableListOf()

    private var resolvedException: UTAny? = null

    private val getClassMethod by lazy { cp.findJcMethod("java.lang.Object", "getClass", emptyList()) }
    private val getMessageMethod by lazy { cp.findJcMethod("java.lang.Throwable", "getMessage", emptyList()) }
    private val servletExceptionGetRootCaseMethod by lazy {
        cp.findJcMethod("jakarta.servlet.ServletException", "getRootCause", emptyList())
    }
    private val getRootCauseMethod by lazy {
        cp.findJcMethod(
            "org.usvm.jvm.rendering.ReflectionUtils",
            "getRootCause",
            emptyList()
        )
    }
    private fun getGetRootCauseMethod(exceptionType: JcType) =
        if (exceptionType.typeName == servletExceptionType.typeName) {
            servletExceptionGetRootCaseMethod
        } else {
            getRootCauseMethod
        }

    private val servletExceptionType by lazy { cp.findType("jakarta.servlet.ServletException") }

    private fun addAssertEqualsCall(expected: UTAny, actual: UTAny) {
        val assertDsl = UTestAssertEqualsCall(expected, actual)
        initStatements.add(assertDsl)
    }

    private fun getResolvedExceptionCached(mvcResult: UTAny): UTAny {
        if (resolvedException == null) {
            val getResolvedExceptionMethod = cp.findJcMethod(
                "org.springframework.test.web.servlet.MvcResult",
                "getResolvedException",
                emptyList()
            )
            resolvedException = UTestMethodCall(mvcResult, getResolvedExceptionMethod, emptyList())
        }
        return resolvedException!!
    }

    fun addResolvedExceptionTypeCheck(expectedType: UTestClassExpression): SpringExceptionMatchersBuilder {
        val mvcResult = testExecBuilder.getExecDSL()
        val resolvedException = getResolvedExceptionCached(mvcResult)
        val type = UTestMethodCall(resolvedException, getClassMethod, emptyList())
        addAssertEqualsCall(expectedType, type)
        return this
    }

    fun addResolvedExceptionMessageCheck(expectedMessage: UTString): SpringExceptionMatchersBuilder {
        val mvcResult = testExecBuilder.getExecDSL()
        val resolvedException = getResolvedExceptionCached(mvcResult)

        val message = UTestMethodCall(resolvedException, getMessageMethod, emptyList())
        addAssertEqualsCall(expectedMessage, message)
        return this
    }

    fun addUnhandledSpringExceptionCheck(
        expectedException: UnhandledSpringException
    ): SpringExceptionMatchersBuilder {
        val mvcResult = testExecBuilder.getExecDSL()
        val getRootCauseMethod = getGetRootCauseMethod(expectedException.wrapperClass.type)
        val rootCause = UTestStaticMethodCall(getRootCauseMethod, listOf(mvcResult))

        val type = UTestMethodCall(rootCause, getClassMethod, emptyList())
        addAssertEqualsCall(expectedException.clazz, type)

        expectedException.message?.let {
            val message = UTestMethodCall(rootCause, getMessageMethod, emptyList())
            addAssertEqualsCall(it, message)
        }

        return this
    }

    fun addUnhandedExceptionCheck(exceptionType: UTestClassExpression): SpringExceptionMatchersBuilder {
        testExecBuilder.wrapInAssertThrows(exceptionType)
        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements
}
