package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.CONSTRUCTOR
import org.jacodb.api.jvm.ext.findDeclaredFieldOrNull
import org.jacodb.api.jvm.ext.findDeclaredMethodOrNull
import org.jacodb.api.jvm.ext.toType
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
    private var generatedTestClass: JcClassOrInterface,
    private var testClassInst: UTestExpression
) {
    companion object {

        const val MOCK_MVC_NAME = "org.springframework.test.web.servlet.MockMvc"

        private const val TEST_CONTEXT_MANAGER_NAME = "org.springframework.test.context.TestContextManager"

        /*
        * DSL STEPS:
        *   ctxManager: TestContextManager = new TestContextManager(<GENERATED-CLASS>.class)
        *   generatedClass: <GENERATED-CLASS> = new <GENERATED-CLASS>()
        *   ctxManager.prepareTestInstance(generatedClass)
        *   mockMvc: MockMvc = generatedClass.<FIELD-WITH-MOCKMVC>
        */

        fun initTestCtx(cp: JcClasspath, generatedTestClass: JcClassOrInterface): SpringTestExecBuilder {
            val testCtxManagerCtorCall = UTestConstructorCall(
                method = testContextManagerCtor(cp),
                args = listOf(UTestClassExpression(generatedTestClass.toType()))
            )

            val ctor = generatedTestClass.findDeclaredMethodOrNull(CONSTRUCTOR)
                ?: error("test class constructor not found")
            val generatedClassCtorCall = UTestConstructorCall(
                method = ctor,
                args = listOf()
            )

            val prepareTestInstanceCall = UTestMethodCall(
                instance = testCtxManagerCtorCall,
                method = testCtxManagerPrepareTestInstance(cp),
                args = listOf(generatedClassCtorCall)
            )

            val mockMvcType = cp.findTypeOrNull(MOCK_MVC_NAME) ?: error("MockMvc type not found")
            val mockMvcField =
                generatedTestClass.findDeclaredFieldOrNull("mockMvc")
                    ?: JcSpringTestClassesFeature.addAutowireField(mockMvcType)

            check(generatedTestClass.findDeclaredFieldOrNull("mockMvc") != null)

            val mockMvc = UTestGetFieldExpression(
                instance = generatedClassCtorCall,
                field = mockMvcField,
            )

            return SpringTestExecBuilder(
                cp = cp,
                initStatements = mutableListOf(prepareTestInstanceCall),
                mockMvcDSL = mockMvc,
                generatedTestClass = generatedTestClass,
                testClassInst = generatedClassCtorCall
            )
        }

        private fun testContextManagerCtor(cp: JcClasspath): JcMethod {
            return cp.findJcMethod(TEST_CONTEXT_MANAGER_NAME, CONSTRUCTOR)
        }

        private fun testCtxManagerPrepareTestInstance(cp: JcClasspath): JcMethod {
            return cp.findJcMethod(TEST_CONTEXT_MANAGER_NAME, "prepareTestInstance")
        }
    }

    val testClassExpr get() = testClassInst

    private val mockMvcPerform: JcMethod by lazy {
        cp.findJcMethod(MOCK_MVC_NAME, "perform")
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
        cp.findJcMethod("org.springframework.test.web.servlet.ResultActions", "andExpect")
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

    fun getExecDSL(): UTestCall {
        check(isPerformed)

        val ignoreMethod = generatedTestClass.findDeclaredMethodOrNull("ignoreResult")
            ?: error("ignoreResult method not found")
        mockMvcDSL = UTestStaticMethodCall(
            method = ignoreMethod,
            args = listOf(mockMvcDSL)
        )

        return mockMvcDSL as UTestCall
    }
}
