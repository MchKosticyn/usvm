package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.int
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestCall
import org.usvm.test.api.UTestCreateArrayExpression
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestIntExpression
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestStaticMethodCall
import org.usvm.test.api.UTestStringExpression
import org.usvm.test.internal.findJcMethod

class SpringMatchersBuilder(
    val cp: JcClasspath
) {
    private val SPRING_RESULT_PACK = "org.springframework.test.web.servlet.result"
    private val initStatements: MutableList<UTestInst> = mutableListOf()
    private val matchers: MutableList<UTestExpression> = mutableListOf()

    private fun addMatcher(matcherName: String, matcherArguments: List<UTAny> = listOf()): UTestCall {
        val createMatcherMethod = cp.findJcMethod(
            "$SPRING_RESULT_PACK.MockMvcResultMatchers",
            matcherName
        ).method

        val matcherDsl = UTestStaticMethodCall(
            method = createMatcherMethod,
            args = matcherArguments
        )

        return matcherDsl
    }

    private fun addCondition(
        matcherSourceDsl: UTestCall,
        conditionName: String,
        conditionArguments: List<UTAny>
    ): UTestExpression {
        val conditionMethod = cp.findJcMethod(
            matcherSourceDsl.method!!.returnType.typeName,
            conditionName
        ).method

        val conditionDsl = UTestMethodCall(
            instance = matcherSourceDsl,
            method = conditionMethod,
            args = conditionArguments
        )
        matchers.add(conditionDsl)

        return conditionDsl
    }

    fun addStatusCheck(int: UTInt): SpringMatchersBuilder {
        addCondition(addMatcher("status"), "is", listOf(int))
        return this
    }

    fun addContentCheck(content: UTString): SpringMatchersBuilder {
        addCondition(addMatcher("content"), "string", listOf(content))
        return this
    }

    fun addHeadersCheck(headers: List<JcSpringHttpHeader>): SpringMatchersBuilder {
        val matcher = addMatcher("header")
        headers.forEach { addCondition(matcher, "stringValues", listOf(it.getName(), it.getValues())) }
        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements

    fun getMatchersDSL(): List<UTestExpression> = matchers
}
