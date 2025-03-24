package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.findClass
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestCall
import org.usvm.test.api.UTestClassExpression
import org.usvm.test.api.UTestConstructorCall
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestGetFieldExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.internal.findJcMethod

class SpringTestExecBuilder private constructor(
    private val cp: JcClasspath,
    private val initStatements: MutableList<UTestInst>,
    private var mockMvcDSL: UTestExpression,
    private var isPerformed: Boolean = false,
) {
    companion object {
        fun initTestCtx(cp: JcClasspath, generatedTestClass: JcClassType?): SpringTestExecBuilder {
            if (generatedTestClass != null) return withPreparedContextFor(cp, generatedTestClass)
            val mockMvc = UTestAllocateMemoryCall(cp.findClass("org.springframework.test.web.servlet.MockMvc"))
            return SpringTestExecBuilder(cp, mutableListOf(), mockMvc)
        }

        /*
        * DSL STEPS:
        *   ctxManager: TestContextManager = new TestContextManager(<GENERATED-CLASS>.class)
        *   generatedClass: <GENERATED-CLASS> = new <GENERATED-CLASS>()
        *   ctxManager.prepareTestInstance(generatedClass)
        *   mockMvc: MockMvc = generatedClass.<FIELD-WITH-MOCKMVC>
        */

        private fun withPreparedContextFor(cp: JcClasspath, generatedTestClass: JcClassType): SpringTestExecBuilder {
            val testCtxManagerName = "org.springframework.test.context.TestContextManager"
            val testCtxManagerCtorCall = UTestConstructorCall(
                method = cp.findJcMethod(testCtxManagerName, "<init>").method,
                args = listOf(UTestClassExpression(generatedTestClass))
            )

            val generatedClassCtorCall = UTestConstructorCall(
                method = cp.findJcMethod(generatedTestClass.typeName, "<init>").method, args = listOf()
            )

            val prepareTestInstanceCall = UTestMethodCall(
                instance = testCtxManagerCtorCall,
                method = cp.findJcMethod(testCtxManagerName, "prepareTestInstance").method,
                args = listOf(generatedClassCtorCall)
            )

            val mockMvc = UTestGetFieldExpression(
                instance = generatedClassCtorCall,
                field = generatedTestClass.fields.first { it.name.contains("mockMvc") }.field,
            )

            val initStatements = mutableListOf<UTestInst>(
                testCtxManagerCtorCall,
                generatedClassCtorCall,
                prepareTestInstanceCall
            )

            return SpringTestExecBuilder(
                cp = cp,
                initStatements = initStatements,
                mockMvcDSL = mockMvc,
            )
        }
    }

    fun addPerformCall(reqDSL: UTestExpression): SpringTestExecBuilder {
        UTestMethodCall(
            instance = mockMvcDSL,
            method = cp.findJcMethod("org.springframework.test.web.servlet.MockMvc", "perform").method,
            args = listOf(reqDSL)
        ).also {
            mockMvcDSL = it
            isPerformed = true
        }
        return this
    }

    fun addAndExpectCall(args: List<UTestExpression>): SpringTestExecBuilder {
        check(isPerformed)

        UTestMethodCall(
            instance = mockMvcDSL,
            method = cp.findJcMethod("org.springframework.test.web.servlet.ResultActions", "andExpect").method,
            args = args
        ).also {
            mockMvcDSL = it
        }
        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements

    fun getExecDSL(): UTestCall {
        check(isPerformed)
        return mockMvcDSL as UTestCall
    }
}
