package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClasspath
import org.usvm.test.api.UTestClassExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestAssertEqualsCall

class SpringExceptionMatchersBuilder (
    private val cp: JcClasspath,
    private val testExecBuilder: SpringTestExecBuilder
) {
    private val initStatements: MutableList<UTestInst> = mutableListOf()

    private var resolvedException: UTAny? = null

    private fun addAssertEqualsCall(expected: UTAny, actual: UTAny) {
        val assertDsl = UTestAssertEqualsCall(expected, actual)
        initStatements.add(assertDsl)
    }

    private fun getResolvedExceptionCached(mvcResult: UTAny): UTAny {
        if (resolvedException == null) {
            val getResolvedExceptionMethod = cp.findJcMethod(
                "org.springframework.test.web.servlet.MvcResult",
                "getResolvedException",
                listOf()
            )
            resolvedException = UTestMethodCall(
                mvcResult,
                getResolvedExceptionMethod,
                listOf()
            )
        }
        return resolvedException!!
    }

    fun addResolvedExceptionTypeCheck(expectedType: UTestClassExpression): SpringExceptionMatchersBuilder {
        val mvcResult = testExecBuilder.getExecDSL()
        val resolvedException = getResolvedExceptionCached(mvcResult)
        val getClassMethod = cp.findJcMethod(
            "java.lang.Object",
            "getClass",
            listOf()
        )
        val type = UTestMethodCall(
            resolvedException,
            getClassMethod,
            listOf()
        )
        addAssertEqualsCall(expectedType, type)
        return this
    }

    fun addResolvedExceptionMessageCheck(expectedMessage: UTString): SpringExceptionMatchersBuilder {
        val mvcResult = testExecBuilder.getExecDSL()
        val resolvedException = getResolvedExceptionCached(mvcResult)
        val getMessageMethod = cp.findJcMethod(
            "java.lang.Throwable",
            "getMessage",
            listOf()
        )
        val message = UTestMethodCall(
            resolvedException,
            getMessageMethod,
            listOf()
        )
        addAssertEqualsCall(expectedMessage, message)
        return this
    }

    fun addUnhandedExceptionCheck(exceptionType: UTestClassExpression): SpringExceptionMatchersBuilder {
        testExecBuilder.wrapInAssertThrows(exceptionType)

        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements
}
