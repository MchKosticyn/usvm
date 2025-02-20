package org.usvm.api

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.MethodNotFoundException
import org.usvm.machine.state.JcState

import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findMethodOrNull
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.toType
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.util.JcTestStateResolver.ResolveMode
import org.usvm.machine.JcContext
import org.usvm.machine.state.concreteMemory.JcConcreteMemory
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
import org.usvm.test.api.UTestConstructorCall
import org.usvm.test.api.UTestCreateArrayExpression
import org.usvm.test.api.UTestDoubleExpression
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestFloatExpression
import org.usvm.test.api.UTestGetFieldExpression
import org.usvm.test.api.UTestGetStaticFieldExpression
import org.usvm.test.api.UTestInst
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


fun JcClasspath.findJcMethod(cName: String, mName: String): JcTypedMethod {
    val method = this.findClass(cName).toType().findMethodOrNull { it.name == mName }
    method?.let { return it }
    throw MethodNotFoundException("$mName not found")
}

fun List<String>.toStringArrayDsl(ctx: JcContext): Pair<UTestCreateArrayExpression, MutableList<UTestInst>> {
    val initDSL = mutableListOf<UTestInst>()
    val stringType = ctx.stringType
    val intType = ctx.cp.int

    val arrayDSL = UTestCreateArrayExpression(
        elementType = stringType,
        size = UTestIntExpression(this.size, intType),
    ).also { initDSL.add(it) }

    this.forEachIndexed { idx, str ->
        UTestArraySetStatement(
            arrayInstance = arrayDSL,
            index = UTestIntExpression(idx, intType),
            setValueExpression = UTestStringExpression(str, stringType),
        ).also { initDSL.add(it) }
    }
    return Pair(arrayDSL, initDSL)
}

// todo:(path for test pipeline) /owners/find

interface SpringReqAttr

data class ParamAttr(
    val name: String,
    val values: List<Any>,
//    val valueType: JcClassOrInterface, TODO: mb use it to generate DSL or concretize? (but it need support from Arthur)
) : SpringReqAttr

data class HeaderAttr(
    val name: String,
    val values: List<Any>,
//    val valueType: JcClassOrInterface, TODO: mb use it to generate DSL or concretize? (but it need support from Arthur)
) : SpringReqAttr

data class SpringReqPath(
    val path: String,
    val pathVariables: List<Any>
)

enum class SpringReqKind {
    GET,
    PUT,
    POST,
    PATCH,
    DELETE;

    override fun toString(): String {
        return when (this) {
            GET -> "get"
            PUT -> "put"
            POST -> "post"
            PATCH -> "patch"
            DELETE -> "delete"
        }
    }

    companion object {
        fun fromString(str: String): SpringReqKind =
            when (str) {
                GET.toString() -> GET
                PUT.toString() -> PUT
                POST.toString() -> POST
                PATCH.toString() -> PATCH
                DELETE.toString() -> DELETE
                else -> throw IllegalArgumentException("Unsupported kind: $str")
            }
    }

}

enum class SpringReqSettings {
    PATH,
    KIND,
}

data class SpringResponse(
    val statusCode: Int,
    // TODO (add needed)
)

class SpringExn

class SpringReqDSLBuilder private constructor(
    private val initStatements: MutableList<UTestInst>,
    private var reqDSL: UTestExpression,
    private val ctx: JcContext
) {
    companion object {

        fun createReq(ctx: JcContext, kind: SpringReqKind, path: SpringReqPath): SpringReqDSLBuilder =
            commonReqDSLBuilder(kind.toString(), ctx, path.path, path.pathVariables)

        private const val MOCK_MVC_REQUEST_BUILDERS_CP =
            "org.springframework.test.web.servlet.request.MockMvcRequestBuilders"

        private fun commonReqDSLBuilder(
            type: String,
            ctx: JcContext,
            path: String,
            pathVariables: List<Any>
        ): SpringReqDSLBuilder {
            val staticMethod = ctx.cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CP, type).method
            val initDSL = mutableListOf<UTestInst>()
            val argsDSL = mutableListOf<UTestExpression>()
            argsDSL.add(UTestStringExpression(path, ctx.stringType))
            argsDSL.addAll(pathVariables.map { UTestStringExpression(it.toString(), ctx.stringType) })

            return SpringReqDSLBuilder(
                initStatements = initDSL,
                reqDSL = UTestStaticMethodCall(staticMethod, argsDSL),
                ctx = ctx
            )
        }
    }

    fun getInitDSL(): List<UTestInst> = initStatements
    fun getDSL() = reqDSL

    fun addParam(attr: ParamAttr): SpringReqDSLBuilder {
        val method = ctx.cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CP, "param").method
        addStrArrOfStrCallDSL(method, attr.name, attr.values)
        return this
    }

    fun addHeader(attr: HeaderAttr): SpringReqDSLBuilder {
        val method = ctx.cp.findJcMethod(MOCK_MVC_REQUEST_BUILDERS_CP, "header").method
        addStrArrOfStrCallDSL(method, attr.name, attr.values)
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

    fun addAttrs(attrs: List<SpringReqAttr>): SpringReqDSLBuilder {
        attrs.forEach { attr ->
            when (attr) {
                is ParamAttr -> addParam(attr)
                is HeaderAttr -> addHeader(attr)
            }
        }
        return this
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

class SpringTestExecDSLBuilder private constructor(
    private val ctx: JcContext,
    private val initStatements: MutableList<UTestInst>,
    private var mockMvcDSL: UTestExpression,
    private var isPerformed: Boolean = false,
) {
    companion object {
        /*
        * DSL STEPS:
        *   ctxManager: TestContextManager = new TestContextManager(<GENERATED-CLASS>.class)
        *   generatedClass: <GENERATED-CLASS> = new <GENERATED-CLASS>()
        *   ctxManager.prepareTestInstance(generatedClass)
        *   mockMvc: MockMvc = generatedClass.<FIELD-WITH-MOCKMVC>
        * */
        fun intiTestCtx(
            ctx: JcContext,
            generatedTestClass: JcClassType,
            fromField: JcField
        ): SpringTestExecDSLBuilder {
            val initStatements = mutableListOf<UTestInst>()

            val testCtxManagerName = "org.springframework.test.context.TestContextManager"
            val testCtxManagerDSL = UTestConstructorCall(
                method = ctx.cp.findJcMethod(testCtxManagerName, "<init>").method,
                args = listOf(UTestClassExpression(generatedTestClass))
            ).also { initStatements.add(it) }

            val generatedClassInstDSL = UTestConstructorCall(
                method = ctx.cp.findJcMethod(generatedTestClass.name, "<init>").method,
                args = listOf()
            ).also { initStatements.add(it) }

            UTestMethodCall(
                instance = testCtxManagerDSL,
                method = ctx.cp.findJcMethod(testCtxManagerName, "prepareTestInstance").method,
                args = listOf(generatedClassInstDSL)
            ).also { initStatements.add(it) }

            val mockMvcDSL = UTestGetFieldExpression(
                instance = generatedClassInstDSL,
                field = fromField,
            ).also { initStatements.add(it) }

            return SpringTestExecDSLBuilder(
                ctx = ctx,
                initStatements = initStatements,
                mockMvcDSL = mockMvcDSL,
            )
        }
    }

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
    fun getExecDSL(): UTestCall {
        assert(isPerformed)
        return mockMvcDSL as UTestCall
    }
}

class SpringMatchersDSLBuilder(
    val ctx: JcContext
) {
    private val SPRING_RESULT_PACK = "org.springframework.test.web.servlet.result"

    private val initStatements: MutableList<UTestInst> = mutableListOf()
    private val matchers: MutableList<UTestExpression> = mutableListOf()

    fun addStatusCheck(int: Int): SpringMatchersDSLBuilder {
        val statusMatcherDSL = UTestStaticMethodCall(
            method = ctx.cp.findJcMethod(
                "$SPRING_RESULT_PACK.MockMvcResultMatchers",
                "status"
            ).method,
            args = listOf()
        ).also { initStatements.add(it) }

        val intDSL = UTestIntExpression(
            value = int,
            type = ctx.cp.int
        ).also { initStatements.add(it) }

        UTestMethodCall(
            instance = statusMatcherDSL,
            method = ctx.cp.findJcMethod("$SPRING_RESULT_PACK.StatusResultMatchers", "is").method,
            args = listOf(intDSL)
        ).also { matchers.add(it) }

        return this
    }

    fun getInitDSL(): List<UTestInst> = initStatements
    fun getMatchersDSL(): List<UTestExpression> = matchers
}


class JcSpringTest private constructor(
    val ctx: JcContext,
    val generatedTestClass: JcClassType,
    /* Request information */
    val reqAttrs: List<SpringReqAttr>,
    val reqKind: SpringReqKind,
    val reqPath: SpringReqPath,
    /* Response information */
    private val _res: SpringResponse?,
    private val _exn: SpringExn?,
//    todo: exn
) {
    companion object {
        val REQUEST_MOD = ResolveMode.MODEL
        val RESPONSE_MOD = ResolveMode.CURRENT

        fun generateFromState(state: JcState): JcSpringTest =
            if (state.res != null)
                generateResponseTest(state)
            else
                generateExnTest(state)

        private fun generateResponseTest(state: JcState): JcSpringTest = JcSpringTest(
            state.ctx,
            getGeneratedClassName(state.ctx.cp),
            getReqAttrs(state),
            getReqKind(state),
            getReqPath(state),
            _res = getSpringResponse(state.ctx.cp, state),
            _exn = null
        )

        private fun generateExnTest(state: JcState): JcSpringTest = JcSpringTest(
            state.ctx,
            getGeneratedClassName(state.ctx.cp),
            getReqAttrs(state),
            getReqKind(state),
            getReqPath(state),
            _res = null,
            _exn = getSpringExn(),
        )

        private fun getSpringExn(): SpringExn {
            TODO()
        }

        private fun getGeneratedClassName(cp: JcClasspath): JcClassType {
            val cl = cp.findClassOrNull("StartSpringTestClass") //TODO: get it from state? (it is generated in runtime)
            assert(cl != null)
            return cl!!.toType()
        }

        private fun getReqKind(state: JcState): SpringReqKind {
            val expr = state.reqSetup[SpringReqSettings.KIND] ?: throw IllegalArgumentException("No path found")
            val type = state.ctx.stringType as JcClassType
            val kind = concretizeSimple(REQUEST_MOD, state, expr, type)
            assert(kind != null)

            return SpringReqKind.fromString(kind as String)
        }

        private fun getReqPath(state: JcState): SpringReqPath {
            fun sortReqParam(path: String, params: Map<String, Any>): List<Any> {
//                TODO: check it
                val paramNames = Regex("\\{([^}]*)}").findAll(path)
                    .map { it.groupValues[1] }
                    .toList()

                return paramNames.map {
                    params.getValue(it)
                }.also { assert(it.size == params.size) }
            }

            val expr = state.reqSetup[SpringReqSettings.PATH] ?: throw IllegalArgumentException("No path found")
            val type = state.ctx.stringType as JcClassType
            val path = concretizeSimple(REQUEST_MOD, state, expr, type)
            assert(path != null)

            val concreteReqParams = mutableMapOf<String, Any>().also { map ->
                state.userDefinedValues.forEach { (key, value) ->
                    if (key.contains("PATH_*".toRegex())) {
                        val name = key.split("_").also { it.subList(1, it.size) }.joinToString("_")
                        concretizeSimple(REQUEST_MOD, state, value.first, value.second).also {
                            assert(it != null) //TODO: is it correct? (param have name? but == null)
                            map[name] = it!!
                        }
                    }
                }
            }

            return SpringReqPath(
                path = path!! as String,
                pathVariables = sortReqParam(path as String, concreteReqParams)
            )
        }

        private fun getReqAttrs(state: JcState): MutableList<SpringReqAttr> {
            fun concretize(expr: UExpr<out USort>, type: JcType) =
                concretizeAsList(REQUEST_MOD, state, expr, type)

            fun getHeaderAttr(name: String, expr: UExpr<out USort>, type: JcType) = concretize(expr, type)?.let {
                HeaderAttr(
                    name = name,
                    values = it
                )
            }

            fun getParamAttr(name: String, expr: UExpr<out USort>, type: JcType) = concretize(expr, type)?.let {
                ParamAttr(
                    name = name,
                    values = it
                )
            }

            return mutableListOf<SpringReqAttr>().also { list ->
                state.userDefinedValues.forEach { (key, value) ->
                    //TODO: (MCHK): null -> no attr
                    when {
                        key.contains("PARAM_*".toRegex()) -> {
                            val name = key.split("_").let { it.subList(1, it.size) }.joinToString("_")
                            getHeaderAttr(name, value.first, value.second)
                        }

                        key.contains("HEADER_*".toRegex()) -> {
                            val name = key.split("_").let { it.subList(1, it.size) }.joinToString("_")
                            getParamAttr(name, value.first, value.second)
                        }

                        else -> null
                    }?.also { list.add(it) }
                }
            }
        }

        private fun getSpringResponse(cp: JcClasspath, state: JcState): SpringResponse {
            assert(state.res != null)
            val expr = state.res ?: throw IllegalArgumentException("No Response")
            val valueExpr = state.models[0].eval(expr)

            val type = cp.int

            // TODO: problem with cast
            val statusCode = concretizeSimple(
                RESPONSE_MOD,
                state,
                valueExpr,
                type
            ) as Int
            return SpringResponse(statusCode)
        }

        private fun concretizeSimple(mode: ResolveMode, state: JcState, expr: UExpr<out USort>, type: JcType) =
            (state.memory as JcConcreteMemory).concretize(
                state, state.models[0].eval(expr), type, mode
            )

        private fun concretizeAsList(mode: ResolveMode, state: JcState, expr: UExpr<out USort>, type: JcType) =
            concretizeSimple(mode, state, expr, type)?.let { value ->
                if (value is Iterable<*>) value.map { it!! }.toList()
                else listOf(value)
            }
    }

    val isSuccess get() = _res != null
    val isFail get() = _exn != null
    val res get() = assert(isSuccess).let { _res!! }
    val exn get() = assert(isFail).let { _exn!! }

    fun generateTestDSL(): UTest {
        val initStatements: MutableList<UTestInst> = mutableListOf()
        val testExecBuilder = SpringTestExecDSLBuilder.intiTestCtx(
            ctx = ctx,
            generatedTestClass = generatedTestClass,
            fromField = generatedTestClass.fields.first { it.name.contains("mockMvc") }.field //TODO: mb error here
        ).also { initStatements.addAll(it.getInitDSL()) }

        val reqDSL = generateReqDSL(reqKind, reqPath, reqAttrs).let { (reqDSL, reqInitDSL) ->
            initStatements.addAll(reqInitDSL)
            reqDSL
        }
        testExecBuilder.addPerformCall(reqDSL)

        val matchersDSL = generateMatchersDSL().let { (matchersDSL, matchersInitDSL) ->
            initStatements.addAll(matchersInitDSL)
            matchersDSL
        }
        matchersDSL.forEach { testExecBuilder.addAndExpectCall(listOf(it)) }

        return UTest(
            initStatements = initStatements,
            callMethodExpression = testExecBuilder.getExecDSL()
        )
    }

    private fun generateMatchersDSL(): Pair<List<UTestExpression>, List<UTestInst>> {
        val matchersBuilder = SpringMatchersDSLBuilder(ctx)

        matchersBuilder.addStatusCheck(res.statusCode)
//      TODO("add more matchers")

        return Pair(matchersBuilder.getMatchersDSL(), matchersBuilder.getInitDSL())
    }

    private fun generateReqDSL(
        reqKind: SpringReqKind,
        reqPath: SpringReqPath,
        reqAttrs: List<SpringReqAttr>
    ): Pair<UTestExpression, List<UTestInst>> {
        val builder = SpringReqDSLBuilder.createReq(ctx, reqKind, reqPath).addAttrs(reqAttrs)
        return Pair(builder.getDSL(), builder.getInitDSL())
    }
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
