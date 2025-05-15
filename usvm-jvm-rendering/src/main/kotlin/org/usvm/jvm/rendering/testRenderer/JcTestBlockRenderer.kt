package org.usvm.jvm.rendering.testRenderer

import com.github.javaparser.ast.ArrayCreationLevel
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.ArrayAccessExpr
import com.github.javaparser.ast.expr.ArrayCreationExpr
import com.github.javaparser.ast.expr.BinaryExpr
import com.github.javaparser.ast.expr.CastExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.LambdaExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.NullLiteralExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.expr.TypeExpr
import com.github.javaparser.ast.type.ReferenceType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.PredefinedPrimitives
import org.usvm.jvm.rendering.baseRenderer.JcBlockRenderer
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.test.api.ArithmeticOperationType
import org.usvm.test.api.ConditionType
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestArithmeticExpression
import org.usvm.test.api.UTestArrayGetExpression
import org.usvm.test.api.UTestArrayLengthExpression
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestBinaryConditionExpression
import org.usvm.test.api.UTestBinaryConditionStatement
import org.usvm.test.api.UTestBooleanExpression
import org.usvm.test.api.UTestByteExpression
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
import org.usvm.test.api.UTestGlobalMock
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
import java.util.IdentityHashMap
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.usvm.jvm.rendering.isVararg
import org.usvm.jvm.util.toTypedMethod
import org.usvm.test.api.UTestAssertThrowsCall
import partitionByKey

open class JcTestBlockRenderer protected constructor(
    override val methodRenderer: JcTestRenderer,
    importManager: JcImportManager,
    identifiersManager: JcIdentifiersManager,
    cp: JcClasspath,
    protected val shouldDeclareVar: Set<UTestExpression>,
    protected val exprCache: IdentityHashMap<UTestExpression, Expression>,
    thrownExceptions: HashSet<ReferenceType>
) : JcBlockRenderer(methodRenderer, importManager, identifiersManager, cp, thrownExceptions) {

    constructor(
        methodRenderer: JcTestRenderer,
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        shouldDeclareVar: Set<UTestExpression>
    ) : this(methodRenderer, importManager, identifiersManager, cp, shouldDeclareVar, IdentityHashMap(), HashSet())

    override fun newInnerBlock(): JcTestBlockRenderer {
        return JcTestBlockRenderer(
            methodRenderer,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            shouldDeclareVar,
            IdentityHashMap(exprCache),
            thrownExceptions
        )
    }

    fun renderInst(inst: UTestInst) = when (inst) {
        is UTestStatement -> renderStatement(inst)
        is UTestExpression -> addExpression(renderExpression(inst))
    }

    protected fun renderStatement(stmt: UTestStatement) {
        when (stmt) {
            is UTestArraySetStatement -> renderArraySetStatement(stmt)
            is UTestBinaryConditionStatement -> renderBinaryConditionStatement(stmt)
            is UTestSetFieldStatement -> renderSetFieldStatement(stmt)
            is UTestSetStaticFieldStatement -> renderSetStaticFieldStatement(stmt)
        }
    }

    protected fun renderExpression(expr: UTestExpression): Expression =
        exprCache.getOrPut(expr) {
            val rendered = doRenderExpression(expr)
            if (shouldDeclareVar.contains(expr))
                renderVarDeclaration(expr.type!!, rendered)
            else
                rendered
        }

    private fun doRenderExpression(expr: UTestExpression): Expression {
        return when (expr) {
            is UTestArithmeticExpression -> renderArithmeticExpression(expr)
            is UTestArrayGetExpression -> renderArrayGetExpression(expr)
            is UTestArrayLengthExpression -> renderArrayLengthExpression(expr)
            is UTestBinaryConditionExpression -> renderBinaryConditionExpression(expr)
            is UTestAllocateMemoryCall -> renderAllocateMemoryCall(expr)
            is UTestConstructorCall -> renderConstructorCall(expr)
            is UTestMethodCall -> renderMethodCall(expr)
            is UTestStaticMethodCall -> renderStaticMethodCall(expr)
            is UTestAssertThrowsCall -> renderAssertThrowCall(expr)
            is UTestCastExpression -> renderCastExpression(expr)
            is UTestClassExpression -> renderClassExpression(expr)
            is UTestCreateArrayExpression -> renderCreateArrayExpression(expr)
            is UTestGetFieldExpression -> renderGetFieldExpression(expr)
            is UTestGetStaticFieldExpression -> renderGetStaticFieldExpression(expr)
            is UTestGlobalMock -> renderGlobalMock(expr)
            is UTestMockObject -> renderMockObject(expr)
            is UTestConstExpression<*> -> renderConstExpression(expr)
        }
    }

    fun renderConstExpression(expr: UTestConstExpression<*>): Expression = when (expr) {
        is UTestBooleanExpression -> renderBooleanExpression(expr)
        is UTestByteExpression -> renderByteExpression(expr)
        is UTestCharExpression -> renderCharExpression(expr)
        is UTestDoubleExpression -> renderDoubleExpression(expr)
        is UTestFloatExpression -> renderFloatExpression(expr)
        is UTestIntExpression -> renderIntExpression(expr)
        is UTestLongExpression -> renderLongExpression(expr)
        is UTestNullExpression -> renderNullExpression(expr)
        is UTestShortExpression -> renderShortExpression(expr)
        is UTestStringExpression -> renderStringExpression(expr)
    }

    open fun renderArraySetStatement(stmt: UTestArraySetStatement) {
        renderArraySetStatement(
            renderExpression(stmt.arrayInstance),
            renderExpression(stmt.index),
            renderExpression(stmt.setValueExpression)
        )
    }

    open fun renderBinaryConditionStatement(stmt: UTestBinaryConditionStatement) {
        val condition = renderBinaryCondition(stmt.conditionType, stmt.lhv, stmt.rhv)
        renderIfStatement(
            condition = condition,
            initThenBody = {
                it as JcTestBlockRenderer
                for (thenStmt in stmt.trueBranch) {
                    it.renderInst(thenStmt)
                }
            },
            initElseBody = {
                it as JcTestBlockRenderer
                for (thenStmt in stmt.trueBranch) {
                    it.renderInst(thenStmt)
                }
            }
        )
    }

    open fun renderSetFieldStatement(stmt: UTestSetFieldStatement) {
        renderSetFieldStatement(
            renderExpression(stmt.instance),
            stmt.field,
            renderExpression(stmt.value)
        )
    }

    open fun renderSetStaticFieldStatement(stmt: UTestSetStaticFieldStatement) {
        renderSetStaticFieldStatement(
            stmt.field,
            renderExpression(stmt.value)
        )
    }

    open fun renderArithmeticExpression(expr: UTestArithmeticExpression): Expression {
        val operation = when (expr.operationType) {
            ArithmeticOperationType.AND -> BinaryExpr.Operator.AND
            ArithmeticOperationType.PLUS -> BinaryExpr.Operator.PLUS
            ArithmeticOperationType.SUB -> BinaryExpr.Operator.MINUS
            ArithmeticOperationType.MUL -> BinaryExpr.Operator.MULTIPLY
            ArithmeticOperationType.DIV -> BinaryExpr.Operator.DIVIDE
            ArithmeticOperationType.REM -> BinaryExpr.Operator.REMAINDER
            ArithmeticOperationType.EQ -> BinaryExpr.Operator.EQUALS
            ArithmeticOperationType.NEQ -> BinaryExpr.Operator.NOT_EQUALS
            ArithmeticOperationType.GT -> BinaryExpr.Operator.GREATER
            ArithmeticOperationType.GEQ -> BinaryExpr.Operator.GREATER_EQUALS
            ArithmeticOperationType.LT -> BinaryExpr.Operator.LESS
            ArithmeticOperationType.LEQ -> BinaryExpr.Operator.LESS_EQUALS
            ArithmeticOperationType.OR -> BinaryExpr.Operator.OR
            ArithmeticOperationType.XOR -> BinaryExpr.Operator.XOR
        }

        return BinaryExpr(
            renderExpression(expr.lhv),
            renderExpression(expr.rhv),
            operation
        )
    }

    open fun renderArrayGetExpression(expr: UTestArrayGetExpression): Expression =
        ArrayAccessExpr(renderExpression(expr.arrayInstance), renderExpression(expr.index))

    open fun renderArrayLengthExpression(expr: UTestArrayLengthExpression): Expression =
        FieldAccessExpr(renderExpression(expr.arrayInstance), "length")

    protected fun renderBinaryCondition(
        conditionType: ConditionType,
        lhv: UTestExpression,
        rhv: UTestExpression
    ): Expression {

        val operation = when (conditionType) {
            ConditionType.EQ -> BinaryExpr.Operator.EQUALS
            ConditionType.NEQ -> BinaryExpr.Operator.NOT_EQUALS
            ConditionType.GEQ -> BinaryExpr.Operator.GREATER_EQUALS
            ConditionType.GT -> BinaryExpr.Operator.GREATER
        }

        return BinaryExpr(
            renderExpression(lhv),
            renderExpression(rhv),
            operation
        )
    }

    open fun renderBinaryConditionExpression(expr: UTestBinaryConditionExpression): Expression {
        val varExpr = renderVarDeclaration(expr.type!!)
        val condition = renderBinaryCondition(expr.conditionType, expr.lhv, expr.rhv)
        renderIfStatement(
            condition = condition,
            initThenBody = {
                it.addExpression(renderAssign(varExpr, renderExpression(expr.trueBranch)))
            },
            initElseBody = {
                it.addExpression(renderAssign(varExpr, renderExpression(expr.elseBranch)))
            }
        )

        return varExpr
    }

    open fun renderAllocateMemoryCall(expr: UTestAllocateMemoryCall): Expression =
        error("Unsafe is not supported")

    open fun renderConstructorCall(expr: UTestConstructorCall): Expression {
        val type = expr.type as JcClassType
        val (args, inlinesVararg) = renderCallArgs(expr.method, expr.args)
        return renderConstructorCall(expr.method, type, args, inlinesVararg)
    }

    open fun renderMethodCall(expr: UTestMethodCall): Expression {
        val (args, inlinesVararg) = renderCallArgs(expr.method, expr.args)
        return renderMethodCall(
            expr.method,
            renderExpression(expr.instance),
            args,
            inlinesVararg
        )
    }

    open fun renderStaticMethodCall(expr: UTestStaticMethodCall): Expression {
        val (args, inlinesVararg) = renderCallArgs(expr.method, expr.args)
        return renderStaticMethodCall(expr.method, args, inlinesVararg)
    }

    open fun renderLambdaExpression(params: List<UTestExpression>, body: List<UTestInst>): Expression {
        val renderedParams = params.map { renderMethodParameter(it.type ?: error("untyped lambda parameter"))}
        val lambdaBodyRenderer = newInnerBlock()
        body.forEach { inst -> lambdaBodyRenderer.renderInst(inst) }
        val lambdaBody = lambdaBodyRenderer.render()
        return LambdaExpr(NodeList(renderedParams), lambdaBody)
    }

    open fun renderAssertThrowCall(expr: UTestAssertThrowsCall): Expression {
        val exceptionType = renderClassExpression(expr.exceptionType)
        val observedLambda = renderLambdaExpression(listOf(), expr.instList)
        return assertThrowsCall(exceptionType, observedLambda)
    }

    private fun renderCallArgs(method: JcMethod, args: List<UTestExpression>): Pair<List<Expression>, Boolean> {
        val typedParams = method.toTypedMethod.parameters

        check(args.size == typedParams.size) {
            "args size != params size in call ${method.name} with ${args.joinToString(" ")}"
        }

        val filteredArgs = args.toMutableList()
        var inlinesVarargs = false

        if (method.isVararg) {
            val vararg = args.last()
            check(vararg is UTestCreateArrayExpression) {
                "vararg arg expected to be array"
            }

            val varargSize = vararg.size
            check(varargSize is UTestIntExpression) {
                "vararg size expected to be int"
            }

            if (varargSize.value == 0) {
                filteredArgs.removeLast()
                inlinesVarargs = true
            }
        }

        return filteredArgs.map { arg -> renderExpression(arg) } to inlinesVarargs
    }

    open fun renderCastExpression(expr: UTestCastExpression): Expression = CastExpr(
        renderType(expr.type),
        renderExpression(expr.expr)
    )

    open fun renderClassExpression(expr: UTestClassExpression): Expression =
        renderClassExpression(expr.type as JcClassType)

    open fun renderBooleanExpression(expr: UTestBooleanExpression): Expression = renderBooleanPrimitive(expr.value)

    open fun renderByteExpression(expr: UTestByteExpression): Expression = renderBytePrimitive(expr.value)

    open fun renderCharExpression(expr: UTestCharExpression): Expression = renderCharPrimitive(expr.value)

    open fun renderDoubleExpression(expr: UTestDoubleExpression): Expression = renderDoublePrimitive(expr.value)

    open fun renderFloatExpression(expr: UTestFloatExpression): Expression = renderFloatPrimitive(expr.value)

    open fun renderIntExpression(expr: UTestIntExpression): Expression = renderIntPrimitive(expr.value)

    open fun renderLongExpression(expr: UTestLongExpression): Expression = renderLongPrimitive(expr.value)

    open fun renderNullExpression(expr: UTestNullExpression): Expression = NullLiteralExpr()

    open fun renderShortExpression(expr: UTestShortExpression): Expression = renderShortPrimitive(expr.value)

    open fun renderStringExpression(expr: UTestStringExpression): Expression {
        val literal = StringLiteralExpr()
        return literal.setString(expr.value)
    }

    open fun renderCreateArrayExpression(expr: UTestCreateArrayExpression): Expression =
        ArrayCreationExpr(
            renderType(expr.elementType, false),
            NodeList(ArrayCreationLevel(renderExpression(expr.size))),
            null
        )

    open fun renderGetFieldExpression(expr: UTestGetFieldExpression): Expression {
        return renderGetField(renderExpression(expr.instance), expr.field)
    }

    open fun renderGetStaticFieldExpression(expr: UTestGetStaticFieldExpression): Expression {
        return renderGetStaticField(expr.field)
    }

    open fun renderGlobalMock(expr: UTestGlobalMock): Expression = TODO("global mocks not yet supported")

    open fun renderMockObject(expr: UTestMockObject): Expression {
        val type = expr.type as JcClassType

        val (staticMethods, instanceMethods) = expr.methods.partitionByKey { method -> method.isStatic }
        if (!staticMethods.isEmpty()) {
            val staticMock = renderMockedStaticVarDeclaration(type.jcClass)
            renderMockObjectMethods(staticMock, staticMethods)
        }

        val (spyFields, instanceFields) = expr.fields.partitionByKey { field -> field.isSpy }
        check(spyFields.size <= 1) {
            "multiple spy fields found"
        }
        val instanceUnderSpy = spyFields.entries.singleOrNull()?.value

        val mockExpr = renderInstanceMockCreationExpressions(type, instanceUnderSpy)

        if (instanceFields.isEmpty() && instanceMethods.isEmpty()) {
            return mockExpr
        }

        val mockVarNamePrefix = if (instanceUnderSpy != null) "spy" else "mocked"
        val mockVar = renderVarDeclaration(type, mockExpr, mockVarNamePrefix)

        exprCache[expr] = mockVar

        for ((field, fieldValue) in instanceFields) {
            val renderedFieldValue = renderExpression(fieldValue)
            renderSetFieldStatement(mockVar, field, renderedFieldValue)
        }

        renderMockObjectMethods(mockVar, instanceMethods)

        return mockVar
    }

    private fun renderMockObjectMethods(mockVar: NameExpr, methods: Map<JcMethod, List<UTestExpression>>) {
        for ((method, mockValues) in methods) {
            if (mockValues.isEmpty())
                continue

            if (method.returnType.typeName == PredefinedPrimitives.Void)
                continue

            val mockInitialization = renderSingleMockObjectMethod(mockVar, method, mockValues)

            addExpression(mockInitialization)
        }
    }

    private fun renderInstanceMockCreationExpressions(type: JcClassType, instanceUnderSpy: UTestExpression?): Expression {
        return when (instanceUnderSpy) {
            null -> {
                mockitoMockMethodCall(type)
            }

            is UTestNullExpression -> {
                mockitoSpyClassMethodCall(type)
            }

            else -> {
                val instanceToSpy = renderExpression(instanceUnderSpy)
                mockitoSpyInstanceMethodCall(instanceToSpy)
            }
        }
    }

    private fun renderSingleMockObjectMethod(
        mockVar: NameExpr,
        method: JcMethod,
        mockValues: List<UTestExpression>
    ): Expression {
        val typedMethod = method.toTypedMethod

        val args = typedMethod.parameters.map { param ->
            when (param.type.typeName) {
                PredefinedPrimitives.Boolean -> mockitoAnyBooleanMethodCall()
                PredefinedPrimitives.Byte -> mockitoAnyByteMethodCall()
                PredefinedPrimitives.Char -> mockitoAnyCharMethodCall()
                PredefinedPrimitives.Short -> mockitoAnyShortMethodCall()
                PredefinedPrimitives.Int -> mockitoAnyIntMethodCall()
                PredefinedPrimitives.Long -> mockitoAnyLongMethodCall()
                PredefinedPrimitives.Float -> mockitoAnyFloatMethodCall()
                PredefinedPrimitives.Double -> mockitoAnyDoubleMethodCall()
                else -> mockitoAnyMethodCall(param.type)
            }
        }

        val mockWhenCall =
            if (method.isStatic)
                renderMockObjectStaticMethodWhenCall(mockVar, method, args)
            else
                renderMockObjectInstanceMethodWhenCall(mockVar, method, args)

        val methodReturnType = typedMethod.returnType

        val mockedMethod = mockValues.fold(mockWhenCall) { mock, nextReturnValue ->
            val renderedMockValue = renderExpression(nextReturnValue)
            val mockValueWithTypeChecked = exprWithGenericsCasted(methodReturnType, renderedMockValue)
            if (nextReturnValue is UTestClassExpression)
                mockitoThenThrowMethodCall(mock, renderedMockValue)
            else
                mockitoThenReturnMethodCall(mock, mockValueWithTypeChecked)
        }

        return mockedMethod
    }

    private fun renderMockObjectInstanceMethodWhenCall(
        mockVar: NameExpr,
        method: JcMethod,
        args: List<Expression>
    ): Expression {
        val methodCall = renderMethodCall(method, mockVar, args, false)
        return mockitoWhenMethodCall(TypeExpr(mockitoClass), methodCall)
    }

    private fun renderMockObjectStaticMethodWhenCall(mockVar: NameExpr, method: JcMethod, args: List<Expression>): Expression {
        val mockedMethodRef = LambdaExpr(NodeList(), renderStaticMethodCall(method, args, false))
        return mockitoWhenMethodCall(mockVar, mockedMethodRef)
    }

    private fun renderMockedStaticVarDeclaration(mockedClass: JcClassOrInterface): NameExpr {
        val mockMethodDeclType = renderClass(mockedClass)

        val mockedStaticType = renderClass("org.mockito.MockedStatic").setTypeArguments(mockMethodDeclType)

        val mockStaticCall = mockitoMockStaticMethodCall(mockedClass)

        val mockStaticUtil = renderVarDeclaration(mockedStaticType, mockStaticCall, "staticMockUtil")

        val mockStaticDefferedClose = MethodCallExpr(mockStaticUtil, "close")
        methodRenderer.trailingExpressions.add(mockStaticDefferedClose)
        return mockStaticUtil
    }
}
