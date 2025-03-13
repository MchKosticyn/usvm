package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.findClass
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestCall
import org.usvm.test.api.UTestClassExpression
import org.usvm.test.api.UTestConstructorCall
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestGetFieldExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestStaticMethodCall

class SpringTestExecBuilder private constructor(
    private val cp: JcClasspath,
    private val initStatements: MutableList<UTestInst>,
    private var mockMvcDSL: UTestExpression,
    private var isPerformed: Boolean = false,
    private var generatedTestClass: JcClassType? = null,
    private var testClassInst: UTestExpression? = null
) {
    companion object {
        fun initTestCtx(cp: JcClasspath, generatedTestClass: JcClassType?): SpringTestExecBuilder {
            if (generatedTestClass != null) return withPreparedContextFor(cp, generatedTestClass)
            val mockMvc = UTestAllocateMemoryCall(cp.findClass("org.springframework.test.web.servlet.MockMvc"))
            return SpringTestExecBuilder(cp, mutableListOf(), mockMvc, isPerformed = false, generatedTestClass, null)
        }

        private const val testContextManagerName = "org.springframework.test.context.TestContextManager"

        private fun testContextManagerCtor(cp: JcClasspath): JcMethod {
            return cp.findJcMethod(testContextManagerName, "<init>").method
        }

        private fun testCtxManagerPrepareTestIntance(cp: JcClasspath): JcMethod {
            return cp.findJcMethod(testContextManagerName, "prepareTestInstance").method
        }

        /*
        * DSL STEPS:
        *   ctxManager: TestContextManager = new TestContextManager(<GENERATED-CLASS>.class)
        *   generatedClass: <GENERATED-CLASS> = new <GENERATED-CLASS>()
        *   ctxManager.prepareTestInstance(generatedClass)
        *   mockMvc: MockMvc = generatedClass.<FIELD-WITH-MOCKMVC>
        */

        private fun withPreparedContextFor(cp: JcClasspath, generatedTestClass: JcClassType): SpringTestExecBuilder {
            val testCtxManagerCtorCall = UTestConstructorCall(
                method = testContextManagerCtor(cp),
                args = listOf(UTestClassExpression(generatedTestClass))
            )

            val generatedClassCtorCall = UTestConstructorCall(
                method = cp.findJcMethod(generatedTestClass.typeName, "<init>").method,
                args = listOf()
            )

            val prepareTestInstanceCall = UTestMethodCall(
                instance = testCtxManagerCtorCall,
                method = testCtxManagerPrepareTestIntance(cp),
                args = listOf(generatedClassCtorCall)
            )

            val mockMvc = UTestGetFieldExpression(
                instance = generatedClassCtorCall,
                field = generatedTestClass.fields.first { it.name.contains("mockMvc") }.field,
            )

            return SpringTestExecBuilder(
                cp = cp,
                initStatements = mutableListOf(prepareTestInstanceCall),
                mockMvcDSL = mockMvc,
                generatedTestClass = generatedTestClass,
                testClassInst = generatedClassCtorCall
            )
        }
    }

    val testClassExpr get() = testClassInst

    private val mockMvcPerform: JcMethod by lazy {
        cp.findJcMethod("org.springframework.test.web.servlet.MockMvc", "perform").method
    }

    fun addPerformCall(reqDSL: UTestExpression): SpringTestExecBuilder {
        check(!isPerformed) { "second perform call" }
        mockMvcDSL = UTestMethodCall(
            instance = mockMvcDSL,
            method = mockMvcPerform,
            args = listOf(reqDSL)
        )
        isPerformed = true

        return this
    }

    private val andExpectAction: JcMethod by lazy {
        cp.findJcMethod("org.springframework.test.web.servlet.ResultActions", "andExpect").method
    }

    fun addAndExpectCall(args: List<UTestExpression>): SpringTestExecBuilder {
        check(isPerformed)

        mockMvcDSL = UTestMethodCall(
            instance = mockMvcDSL,
            method = andExpectAction,
            args = args
        )

        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements

    fun getExecDSL(shouldIgnoreResult: Boolean = false): UTestCall {
        check(isPerformed)
        check(!shouldIgnoreResult || generatedTestClass != null)

        if (shouldIgnoreResult) {
            mockMvcDSL = UTestStaticMethodCall(
                method = cp.findJcMethod(generatedTestClass!!.typeName, "ignoreResult").method,
                args = listOf(mockMvcDSL)
            )
        }

        return mockMvcDSL as UTestCall
    }
}
