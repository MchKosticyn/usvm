package org.usvm.test.internal

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
import org.usvm.test.api.spring.JcSpringHttpHeader
import org.usvm.test.api.stringType

class SpringMatchersBuilder(
    val cp: JcClasspath
) {
    private val SPRING_RESULT_PACK = "org.springframework.test.web.servlet.result"
    private val initStatements: MutableList<UTestInst> = mutableListOf()
    private val matchers: MutableList<UTestExpression> = mutableListOf()

    private fun wrapStringList(list: List<Any>): UTestCreateArrayExpression {
        val listDsl = UTestCreateArrayExpression(cp.stringType, UTestIntExpression(list.size, cp.int))
        val listInitializer = List(list.size) {
            UTestArraySetStatement(
                listDsl,
                UTestIntExpression(it, cp.int),
                UTestStringExpression(list[it].toString(), cp.stringType)
            )
        }
        initStatements.addAll(listOf(listDsl) + listInitializer)
        return listDsl
    }

    @Suppress("UNCHECKED_CAST")
    private fun wrapArgument(argument: Any): UTestExpression {
        // TODO: other types #AA
        if (argument is List<*>)
            return wrapStringList(argument as List<Any>)
        return when (argument.javaClass) {
            Integer::class.java -> UTestIntExpression(argument as Int, cp.int)
            String::class.java -> UTestStringExpression(argument as String, cp.stringType)
            else -> error("TODO #AA")
        }
    }

    private fun addMatcher(matcherName: String, matcherArguments: List<Any> = listOf()): UTestCall {
        val createMatcherMethod = cp.findJcMethod(
            "$SPRING_RESULT_PACK.MockMvcResultMatchers",
            matcherName
        ).method

        val matcherDsl = UTestStaticMethodCall(
            method = createMatcherMethod,
            args = matcherArguments.map { wrapArgument(it) }.toList()
        ).also { initStatements.add(it) }

        return matcherDsl
    }

    private fun addCondition(
        matcherSourceDsl: UTestCall,
        conditionName: String,
        conditionArguments: List<Any> = listOf()
    ): UTestExpression {
        val conditionMethod = cp.findJcMethod(
            matcherSourceDsl.method!!.returnType.typeName,
            conditionName
        ).method

        val conditionDsl = UTestMethodCall(
            instance = matcherSourceDsl,
            method = conditionMethod,
            args = conditionArguments.map { wrapArgument(it) }.toList()
        ).also { matchers.add(it) }

        return conditionDsl
    }

    fun addStatusCheck(int: Int): SpringMatchersBuilder {
        addCondition(addMatcher("status"), "is", listOf(int))
        return this
    }

    fun addContentCheck(content: String): SpringMatchersBuilder {
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
