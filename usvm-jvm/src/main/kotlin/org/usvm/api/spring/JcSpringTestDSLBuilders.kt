package org.usvm.api.spring

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.int
import org.usvm.jvm.util.stringType
import org.usvm.machine.JcContext
import org.usvm.test.api.ArithmeticOperationType
import org.usvm.test.api.ConditionType
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestArithmeticExpression
import org.usvm.test.api.UTestArrayGetExpression
import org.usvm.test.api.UTestArrayLengthExpression
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestBinaryConditionExpression
import org.usvm.test.api.UTestBinaryConditionStatement
import org.usvm.test.api.UTestBooleanExpression
import org.usvm.test.api.UTestByteExpression
import org.usvm.test.api.UTestCall
import org.usvm.test.api.UTestCastExpression
import org.usvm.test.api.UTestCharExpression
import org.usvm.test.api.UTestClassExpression
import org.usvm.test.api.UTestConstExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestConstructorCall
import org.usvm.test.api.UTestCreateArrayExpression
import org.usvm.test.api.UTestDoubleExpression
import org.usvm.test.api.UTestFloatExpression
import org.usvm.test.api.UTestGetFieldExpression
import org.usvm.test.api.UTestGetStaticFieldExpression
import org.usvm.test.api.UTestIntExpression
import org.usvm.test.api.UTestLongExpression
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestMockObject
import org.usvm.test.api.UTestNullExpression
import org.usvm.test.api.UTestSetFieldStatement
import org.usvm.test.api.UTestSetStaticFieldStatement
import org.usvm.test.api.UTestShortExpression
import org.usvm.test.api.UTestStatement
import org.usvm.test.api.UTestStaticMethodCall
import org.usvm.test.api.UTestStringExpression
import org.usvm.util.name


class SpringTestExecDSLBuilder private constructor(
    private val ctx: JcContext,
    private val initStatements: MutableList<UTestStatement>,
    private var mockMvcDSL: UTestExpression,
    private var isPerformed: Boolean = false,
    private var generatedTestClass: JcClassType,
    private var testClassInstDSL: UTestExpression
) {
    companion object {
        /*
        * DSL STEPS:
        *   ctxManager: TestContextManager = new TestContextManager(<GENERATED-CLASS>.class)
        *   generatedClass: <GENERATED-CLASS> = new <GENERATED-CLASS>()
        *   ctxManager.prepareTestInstance(generatedClass)
        *   mockMvc: MockMvc = generatedClass.<FIELD-WITH-MOCKMVC>
        * */
        fun initTestCtx(
            ctx: JcContext,
            generatedTestClass: JcClassType,
            fromField: JcField
        ): SpringTestExecDSLBuilder {
            val initStatements = mutableListOf<UTestStatement>()

            val testCtxManagerName = "org.springframework.test.context.TestContextManager"
            val testCtxManagerDSL = UTestConstructorCall(
                method = ctx.cp.findJcMethod(testCtxManagerName, "<init>").method,
                args = listOf(UTestClassExpression(generatedTestClass))
            )

            val generatedClassInstDSL = UTestConstructorCall(
                method = ctx.cp.findJcMethod(generatedTestClass.name, "<init>").method,
                args = listOf()
            )

            UTestMethodCall(
                instance = testCtxManagerDSL,
                method = ctx.cp.findJcMethod(testCtxManagerName, "prepareTestInstance").method,
                args = listOf(generatedClassInstDSL)
            )

            val mockMvcDSL = UTestGetFieldExpression(
                instance = generatedClassInstDSL,
                field = fromField,
            )

            return SpringTestExecDSLBuilder(
                ctx = ctx,
                initStatements = initStatements,
                mockMvcDSL = mockMvcDSL,
                generatedTestClass = generatedTestClass,
                testClassInstDSL = generatedClassInstDSL
            )
        }
    }

    fun getTestClassInstance() = testClassInstDSL

    fun addPerformCall(reqDSL: UTestExpression): SpringTestExecDSLBuilder {
        UTestMethodCall(
            instance = mockMvcDSL,
            method = ctx.cp.findJcMethod("org.springframework.test.web.servlet.MockMvc", "perform").method,
            args = listOf(reqDSL)
        ).also {
            mockMvcDSL = it
            isPerformed = true
        }
        return this
    }

    fun addAndExpectCall(args: List<UTestExpression>): SpringTestExecDSLBuilder {
        assert(isPerformed)

        UTestMethodCall(
            instance = mockMvcDSL,
            method = ctx.cp.findJcMethod("org.springframework.test.web.servlet.ResultActions", "andExpect").method,
            args = args
        ).also {
            mockMvcDSL = it
        }
        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements

    fun getIgnoreDsl(): UTestCall {
        assert(isPerformed)
        UTestStaticMethodCall(
            method = ctx.cp.findJcMethod(generatedTestClass.name, "ignoreResult").method,
            args = listOf(mockMvcDSL)
        ).also {
            mockMvcDSL = it
        }
        return mockMvcDSL as UTestCall
    }
}

class SpringMatchersDSLBuilder(
    val ctx: JcContext
) {
    private val SPRING_RESULT_PACK = "org.springframework.test.web.servlet.result"

    private val initStatements: MutableList<UTestStatement> = mutableListOf()
    private val matchers: MutableList<UTestExpression> = mutableListOf()

    private fun wrapStringList(list: List<Any>): UTestCreateArrayExpression {
        val listDsl = UTestCreateArrayExpression(ctx.cp.stringType(), UTestIntExpression(list.size, ctx.cp.int))
        val listInitializer = List(list.size) {
            UTestArraySetStatement(
                listDsl,
                UTestIntExpression(it, ctx.cp.int),
                // TODO: Learn how to do object expression #AA
                UTestStringExpression(list[it].toString(), ctx.stringType)
            )
        }
        initStatements.addAll(listInitializer)
        return listDsl
    }

    @Suppress("UNCHECKED_CAST")
    private fun wrapArgument(argument: Any): UTestExpression {
        // TODO: other types #AA
        if (argument is List<*>)
            return wrapStringList(argument as List<Any>)
        return when (argument.javaClass) {
            Integer::class.java -> UTestIntExpression(argument as Int, ctx.cp.int)
            String::class.java -> UTestStringExpression(argument as String, ctx.cp.stringType())
            else -> error("TODO #AA")
        }
    }

    private fun addMatcher(matcherName: String, matcherArguments: List<Any> = listOf()): UTestCall {
        val createMatcherMethod = ctx.cp.findJcMethod(
            "$SPRING_RESULT_PACK.MockMvcResultMatchers",
            matcherName
        ).method

        val matcherDsl = UTestStaticMethodCall(
            method = createMatcherMethod,
            args = matcherArguments.map { wrapArgument(it) }.toList()
        )

        return matcherDsl
    }

    private fun addCondition(matcherSourceDsl: UTestCall, conditionName: String, conditionArguments: List<Any> = listOf()): UTestExpression {
        val conditionMethod = ctx.cp.findJcMethod(
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

    fun addStatusCheck(int: Int): SpringMatchersDSLBuilder {
        addCondition(addMatcher("status"), "is", listOf(int))
        return this
    }

    fun addContentCheck(content: String): SpringMatchersDSLBuilder {
        addCondition(addMatcher("content"), "string", listOf(content))
        return this
    }

    fun addHeadersCheck(headers: List<JcSpringHttpHeader>): SpringMatchersDSLBuilder {
        val matcher = addMatcher("header")
        headers.forEach { addCondition(matcher, "stringValues", listOf(it.getName(), it.getValues())) }
        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements
    fun getMatchersDSL(): List<UTestExpression> = matchers
}


class SpringReqDSLBuilder private constructor(
    private val initStatements: MutableList<UTestStatement>,
    private var reqDSL: UTestExpression,
    private val ctx: JcContext
) {
    companion object {

        fun createRequest(ctx: JcContext, method: JcSpringRequestMethod, path: String, pathVariables: List<Any?>): SpringReqDSLBuilder =
            commonReqDSLBuilder(ctx, method, path, pathVariables)

        private const val MOCK_MVC_REQUEST_BUILDERS_CP =
            "org.springframework.test.web.servlet.request.MockMvcRequestBuilders"

        private const val MOCK_HTTP_SERVLET_REQUEST_BUILDER_CP =
            "org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder"

        private fun commonReqDSLBuilder(
            ctx: JcContext,
            method: JcSpringRequestMethod,
            path: String,
            pathVariables: List<Any?>
        ): SpringReqDSLBuilder {
            val requestMethodName = method.name.lowercase()
            val staticMethod = ctx.cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CP, requestMethodName).method
            val initDSL = mutableListOf<UTestStatement>()
            val pathArgs = pathVariables.map { it }
            val pathArgsArray = UTestCreateArrayExpression(ctx.stringType, UTestIntExpression(pathArgs.size, ctx.cp.int))
            val pathArgsInitializer = List(pathArgs.size) {
                UTestArraySetStatement(
                    pathArgsArray,
                    UTestIntExpression(it, ctx.cp.int),
                    // TODO: Learn how to do object expression #AA
                    UTestStringExpression(pathArgs[it].toString(), ctx.stringType)
                )
            }
            initDSL.addAll(pathArgsInitializer)
            val argsDSL = mutableListOf<UTestExpression>()
            argsDSL.add(UTestStringExpression(path, ctx.stringType))
            argsDSL.add(pathArgsArray)
            return SpringReqDSLBuilder(
                initStatements = initDSL,
                reqDSL = UTestStaticMethodCall(staticMethod, argsDSL),
                ctx = ctx
            )
        }
    }

    fun getInitDSL(): List<UTestInst> = initStatements
    fun getDSL() = reqDSL

    fun addParameter(attr: JcSpringHttpParameter): SpringReqDSLBuilder {
        val method = ctx.cp.findJcMethod(MOCK_HTTP_SERVLET_REQUEST_BUILDER_CP, "param").method
        addStrArrOfStrCallDSL(method, attr.getName(), attr.getValues())
        return this
    }

    fun addHeader(attr: JcSpringHttpHeader): SpringReqDSLBuilder {
        val method = ctx.cp.findJcMethod(MOCK_HTTP_SERVLET_REQUEST_BUILDER_CP, "header").method
        addStrArrOfStrCallDSL(method, attr.getName(), attr.getValues())
        return this
    }

    private fun addStrArrOfStrCallDSL(mName: JcMethod, str: String, arrOfStr: List<Any>) {
        val argsDSL = mutableListOf<UTestExpression>()
        argsDSL.add(UTestStringExpression(str, ctx.stringType))
        argsDSL.addAll(arrOfStr.map { UTestStringExpression(it.toString(), ctx.stringType) })
        UTestMethodCall(
            instance = reqDSL,
            method = mName,
            args = argsDSL,
        ).also { reqDSL = it }
    }

    /*
     *
     * [ ] accept(String... mediaTypes)
     * [ ] accept(MediaType... mediaTypes)
     * [ ] characterEncoding(String encoding)
     * [ ] characterEncoding(Charset encoding)
     * [ ] content(byte[] content)
     * [ ] content(String content)
     * [ ] contentType(String contentType)
     * [ ] contentType(MediaType contentType)
     * [ ] contextPath(String contextPath)
     * [ ] cookie(Cookie... cookies)
     * [ ] flashAttr(String name, Object value)
     * [ ] flashAttrs(Map<String,Object> flashAttributes)
     * [ ] formField(String name, String... values)
     * [ ] formFields(MultiValueMap<String,String> formFields)
     * TODO: [ ] header(String name, Object... values)
     * [ ] headers(HttpHeaders httpHeaders)
     * [ ] locale(Locale locale)
     * [ ] locale(Locale... locales)
     * TODO: [ ] param(String name, String... values)
     * [ ] params(MultiValueMap<String,String> params)
     * [ ] pathInfo(String pathInfo)
     * [ ] principal(Principal principal)
     * [ ] queryParam(String name, String... values)
     * [ ] queryParams(MultiValueMap<String,String> params)
     * [ ] remoteAddress(String remoteAddress)
     * [ ] requestAttr(String name, Object value)
     * [ ] secure(boolean secure)
     * [ ] servletPath(String servletPath)
     * [ ] session(MockHttpSession session)
     * [ ] sessionAttr(String name, Object value)
     * [ ] sessionAttrs(Map<String,Object> sessionAttributes)
     * [ ] uri(String uriTemplate, Object... uriVariables)
     * [ ] uri(URI uri)
     * [ ] with(RequestPostProcessor postProcessor)
     * */

}


class DSLInternalShower private constructor() {
    /*
    * Deepseek generated class for primitive DSL representation
    * - for internal development use only (DEBUG)
    * - without syntactic guarantees for the correctness of the generated Java code
    * -
    * */
    companion object {
        fun toStringUTest(uTest: UTest) = uTest.initStatements.foldIndexed("") { index, acc, uTestInst ->
            acc + "INIT($index):\n ${toStringDSLCode(uTestInst)}\n"
        }.let { it + "EXEC:\n${toStringDSLCode(uTest.callMethodExpression)}" }

        fun toStringDSLCode(dsl: UTestInst): String = when (dsl) {
            is UTestExpression -> generateJavaCode(dsl)
            is UTestStatement -> generateJavaCode(dsl)
        }

        private fun generateJavaCode(expr: UTestExpression): String = when (expr) {
            is UTestMockObject -> generateMockObjectCode(expr)
            is UTestMethodCall -> generateMethodCallCode(expr)
            is UTestStaticMethodCall -> generateStaticMethodCallCode(expr)
            is UTestConstructorCall -> generateConstructorCallCode(expr)
            is UTestAllocateMemoryCall -> generateAllocateMemoryCallCode(expr)
            is UTestBinaryConditionExpression -> generateBinaryConditionExpressionCode(expr)
            is UTestArithmeticExpression -> generateArithmeticExpressionCode(expr)
            is UTestGetStaticFieldExpression -> generateGetStaticFieldExpressionCode(expr)
            is UTestConstExpression<*> -> generateConstExpressionCode(expr)
            is UTestGetFieldExpression -> generateGetFieldExpressionCode(expr)
            is UTestArrayLengthExpression -> generateArrayLengthExpressionCode(expr)
            is UTestArrayGetExpression -> generateArrayGetExpressionCode(expr)
            is UTestCreateArrayExpression -> generateCreateArrayExpressionCode(expr)
            is UTestCastExpression -> generateCastExpressionCode(expr)
            is UTestClassExpression -> generateClassExpressionCode(expr)
            else -> throw IllegalArgumentException("Unsupported expression type: ${expr::class.java.name}")
        }

        private fun generateJavaCode(statement: UTestStatement): String = when (statement) {
            is UTestSetFieldStatement -> generateSetFieldStatementCode(statement)
            is UTestSetStaticFieldStatement -> generateSetStaticFieldStatementCode(statement)
            is UTestBinaryConditionStatement -> generateBinaryConditionStatementCode(statement)
            is UTestArraySetStatement -> generateArraySetStatementCode(statement)
            else -> throw IllegalArgumentException("Unsupported statement type: ${statement::class.java.name}")
        }

// Extension functions for common patterns

        private fun generateBinaryConditionCode(
            conditionType: ConditionType, lhv: UTestExpression, rhv: UTestExpression
        ): String {
            val condition = when (conditionType) {
                ConditionType.EQ -> "=="
                ConditionType.NEQ -> "!="
                ConditionType.GEQ -> ">="
                ConditionType.GT -> ">"
            }
            return "${generateJavaCode(lhv)} $condition ${generateJavaCode(rhv)}"
        }

        private fun generateArithmeticOperationCode(
            operationType: ArithmeticOperationType, lhv: UTestExpression, rhv: UTestExpression
        ): String {
            val operation = when (operationType) {
                ArithmeticOperationType.PLUS -> "+"
                ArithmeticOperationType.SUB -> "-"
                ArithmeticOperationType.MUL -> "*"
                ArithmeticOperationType.DIV -> "/"
                ArithmeticOperationType.REM -> "%"
                else -> throw IllegalArgumentException("Unsupported arithmetic operation: $operationType")
            }
            return "${generateJavaCode(lhv)} $operation ${generateJavaCode(rhv)}"
        }

// Implementation of specific code generators

        private fun generateMockObjectCode(mock: UTestMockObject): String {
            val className = mock.type.javaClass.name
            val fieldsCode = mock.fields.entries.joinToString("\n") { (field, value) ->
                "        ${field.name} = ${generateJavaCode(value)};"
            }
            val methodsCode = mock.methods.entries.joinToString("\n") { (method, args) ->
                "        ${method.name}(${args.joinToString(", ") { generateJavaCode(it) }});"
            }
            return """
        $className mock = new $className() {
            $fieldsCode
            $methodsCode
        };
    """.trimIndent()
        }

        private fun generateMethodCallCode(call: UTestMethodCall) =
            "${generateJavaCode(call.instance)}.${call.method.name}(${call.args.joinToString(", ") { generateJavaCode(it) }})"

        private fun generateStaticMethodCallCode(call: UTestStaticMethodCall) =
            "${call.method.enclosingClass.name}.${call.method.name}(${call.args.joinToString(", ") { generateJavaCode(it) }})"

        private fun generateConstructorCallCode(call: UTestConstructorCall) =
            "(new ${call.method.enclosingClass.name}.${call.method.name}(${
                call.args.joinToString(", ") {
                    generateJavaCode(
                        it
                    )
                }
            }))"

        private fun generateAllocateMemoryCallCode(call: UTestAllocateMemoryCall) = "UNSAFE_MAGIC.alloc(${call.clazz.name})"

        private fun generateBinaryConditionExpressionCode(expr: UTestBinaryConditionExpression) = "(${
            generateBinaryConditionCode(
                expr.conditionType, expr.lhv, expr.rhv
            )
        }) ? ${generateJavaCode(expr.trueBranch)} : ${generateJavaCode(expr.elseBranch)}"


        private fun generateArithmeticExpressionCode(expr: UTestArithmeticExpression) =
            "(${generateArithmeticOperationCode(expr.operationType, expr.lhv, expr.rhv)})"


        private fun generateGetStaticFieldExpressionCode(expr: UTestGetStaticFieldExpression) =
            "${expr.field.enclosingClass.name}.${expr.field.name}.${expr.field.name}"


        private fun generateConstExpressionCode(expr: UTestConstExpression<*>) = when (expr) {
            is UTestBooleanExpression -> expr.value.toString()
            is UTestByteExpression -> expr.value.toString()
            is UTestShortExpression -> expr.value.toString()
            is UTestIntExpression -> expr.value.toString()
            is UTestLongExpression -> "${expr.value}L"
            is UTestFloatExpression -> "${expr.value}f"
            is UTestDoubleExpression -> expr.value.toString()
            is UTestCharExpression -> "'${expr.value}'"
            is UTestStringExpression -> "\"${expr.value}\""
            is UTestNullExpression -> "null"
            else -> throw IllegalArgumentException("Unsupported constant expression type: ${expr::class.java.name}")
        }

        private fun generateGetFieldExpressionCode(expr: UTestGetFieldExpression) =
            "${generateJavaCode(expr.instance)}.${expr.field.name}"


        private fun generateArrayLengthExpressionCode(expr: UTestArrayLengthExpression) =
            "${generateJavaCode(expr.arrayInstance)}.length"

        private fun generateArrayGetExpressionCode(expr: UTestArrayGetExpression) =
            "${generateJavaCode(expr.arrayInstance)}[${generateJavaCode(expr.index)}]"


        private fun generateCreateArrayExpressionCode(expr: UTestCreateArrayExpression) =
            "new ${expr.elementType.typeName}[${generateJavaCode(expr.size)}]"


        private fun generateCastExpressionCode(expr: UTestCastExpression) =
            "(${expr.type.typeName}) ${generateJavaCode(expr.expr)}"

        private fun generateClassExpressionCode(expr: UTestClassExpression) = "${expr.type.typeName}.class"

        private fun generateSetFieldStatementCode(statement: UTestSetFieldStatement) =
            "${generateJavaCode(statement.instance)}.${statement.field.name} = ${generateJavaCode(statement.value)};"


        private fun generateSetStaticFieldStatementCode(statement: UTestSetStaticFieldStatement) =
            "${statement.field.enclosingClass.name}.${statement.field.name} = ${generateJavaCode(statement.value)};"


        private fun generateBinaryConditionStatementCode(statement: UTestBinaryConditionStatement): String {
            val condition = generateBinaryConditionCode(statement.conditionType, statement.lhv, statement.rhv)
            val trueBranchCode = statement.trueBranch.joinToString("\n") { generateJavaCode(it) }
            val elseBranchCode = statement.elseBranch.joinToString("\n") { generateJavaCode(it) }
            return """
        if ($condition) {
            $trueBranchCode
        } else {
            $elseBranchCode
        }
    """.trimIndent()
        }

        private fun generateArraySetStatementCode(statement: UTestArraySetStatement): String {
            return "${generateJavaCode(statement.arrayInstance)}[${generateJavaCode(statement.index)}] = ${
                generateJavaCode(
                    statement.setValueExpression
                )
            };"
        }

    }
}