package org.usvm.test.internal

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.int
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestIntExpression
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestStaticMethodCall

class SpringMatchersBuilder(
    val cp: JcClasspath
) {
    private val SPRING_RESULT_PACK = "org.springframework.test.web.servlet.result"
    private val initStatements: MutableList<UTestInst> = mutableListOf()
    private val matchers: MutableList<UTestExpression> = mutableListOf()

    fun addStatusCheck(int: Int): SpringMatchersBuilder {
        val statusMatcherDSL = UTestStaticMethodCall(
            method = cp.findJcMethod(
                "$SPRING_RESULT_PACK.MockMvcResultMatchers",
                "status"
            ).method,
            args = listOf()
        )
        initStatements.add(statusMatcherDSL)

        val intDSL = UTestIntExpression(
            value = int,
            type = cp.int
        )
        initStatements.add(intDSL)

        val call = UTestMethodCall(
            instance = statusMatcherDSL,
            method = cp.findJcMethod("$SPRING_RESULT_PACK.StatusResultMatchers", "is").method,
            args = listOf(intDSL)
        )
        matchers.add(call)

        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements

    fun getMatchersDSL(): List<UTestExpression> = matchers
}