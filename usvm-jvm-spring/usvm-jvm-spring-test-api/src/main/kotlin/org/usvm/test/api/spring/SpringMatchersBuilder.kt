package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClasspath
import org.usvm.test.api.UTestCall
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestStaticMethodCall

class SpringMatchersBuilder(
    private val cp: JcClasspath,
    private val testExecBuilder: SpringTestExecBuilder
) {
    private val SPRING_RESULT_PACK = "org.springframework.test.web.servlet.result"
    private val initStatements: MutableList<UTestInst> = mutableListOf()

    private fun addMatcher(matcherName: String, matcherArguments: List<UTAny> = listOf()): UTestCall {
        val createMatcherMethod = cp.findJcMethod(
            "$SPRING_RESULT_PACK.MockMvcResultMatchers",
            matcherName
        )

        val matcherDsl = UTestStaticMethodCall(
            method = createMatcherMethod,
            args = matcherArguments
        )

        return matcherDsl
    }

    private fun addCondition(
        matcherSourceDsl: UTestCall,
        conditionName: String,
        conditionArguments: List<UTAny>,
        conditionParameters: List<String>
    ): UTestExpression {
        val conditionMethod = cp.findJcMethod(
            matcherSourceDsl.method!!.returnType.typeName,
            conditionName,
            conditionParameters
        )

        val conditionDsl = UTestMethodCall(
            instance = matcherSourceDsl,
            method = conditionMethod,
            args = conditionArguments
        )

        testExecBuilder.addAndExpectCall(listOf(conditionDsl))

        return conditionDsl
    }

    fun addStatusCheck(status: UTAny): SpringMatchersBuilder {
        addCondition(
            addMatcher("status"),
            "is",
            listOf(status),
            listOf("int")
        )
        return this
    }

    fun addContentCheck(content: UTString): SpringMatchersBuilder {
        addCondition(
            addMatcher("content"),
            "string",
            listOf(content),
            listOf("java.lang.String")
        )
        return this
    }

    fun addHeadersCheck(headers: List<JcSpringHttpHeader>): SpringMatchersBuilder {
        val matcher = addMatcher("header")
        headers.forEach {
            addCondition(
                matcher,
                "stringValues",
                listOf(it.getName(), it.getValues()),
                listOf("java.lang.String[]")
            )
        }
        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements
}
