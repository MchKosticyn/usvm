package org.usvm.jvm.rendering

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.ArrayCreationLevel
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.ArrayAccessExpr
import com.github.javaparser.ast.expr.ArrayCreationExpr
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.BinaryExpr
import com.github.javaparser.ast.expr.BooleanLiteralExpr
import com.github.javaparser.ast.expr.CastExpr
import com.github.javaparser.ast.expr.CharLiteralExpr
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.DoubleLiteralExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.IntegerLiteralExpr
import com.github.javaparser.ast.expr.LongLiteralExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NullLiteralExpr
import com.github.javaparser.ast.expr.ObjectCreationExpr
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.expr.TypeExpr
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.Statement
import com.github.javaparser.ast.type.ClassOrInterfaceType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.ext.toType
import org.usvm.jvm.rendering.Utils.parseClassOrInterface
import org.usvm.jvm.rendering.Utils.tryAddThrownException
import org.usvm.test.api.ArithmeticOperationType
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

open class JcTestRenderer(
    importManager: JcImportManager,
    override val classRenderer: JcTestClassRenderer,
    name: SimpleName,
    private val test: UTest,
    testAnnotation: AnnotationExpr,
): JcMethodRenderer(
    importManager,
    classRenderer,
    name,
    NodeList(),
    NodeList(testAnnotation),
    NodeList(),
    voidType
) {

    open fun requireVarDeclarationOf(expr: UTestExpression): Boolean = false

    private val shouldDeclareVar: IdentityHashMap<UTestExpression, Unit> = IdentityHashMap()

    inner class JcExprUsageVisitor: JcTestVisitor() {

        private val exprCache = IdentityHashMap<UTestExpression, Unit>()

        override fun visitExpr(expr: UTestExpression) {
            if (expr !is UTestConstExpression<*> && exprCache.put(expr, Unit) != null || requireVarDeclarationOf(expr))
                // Multiple usage of expression
                shouldDeclareVar[expr] = Unit

            super.visitExpr(expr)
        }
    }

    private val exprCache: IdentityHashMap<UTestExpression, Expression> = IdentityHashMap()

    fun render(test: UTest) {
        JcExprUsageVisitor().visitTest(test)

        for (inst in test.initStatements)
            renderInst(inst)

        renderInst(test.callMethodExpression)
    }

    protected fun renderInst(inst: UTestInst) =
        when (inst) {
            is UTestStatement -> renderStatement(inst)
            is UTestExpression -> body.addExpression(renderExpression(inst))
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
            if (shouldDeclareVar.containsKey(expr))
                body.renderVarDeclaration(expr.type!!, rendered)
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
//            is UTestAssertThrowsExpression -> renderAssertThrowsExpression(expr)
//            is UTestAssertConditionExpression -> renderAssertConditionExpression(expr)
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

    private fun collectCallExceptions(expr: UTestCall) {
        expr.method?.exceptions?.forEach { exc -> methodDeclaration.tryAddThrownException(parseClassOrInterface(exc.typeName.replace("$", "."))) }
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

//    open fun renderAssertThrowsExpression(expr: UTestAssertThrowsExpression): Expression {
//        TODO()
//    }
//
//    open fun renderAssertConditionExpression(expr: UTestAssertConditionExpression): Expression {
//        TODO()
//    }

    open fun renderArraySetStatement(stmt: UTestArraySetStatement): Statement = ExpressionStmt(
        AssignExpr(
            ArrayAccessExpr(renderExpression(stmt.arrayInstance), renderExpression(stmt.index)),
            renderExpression(stmt.setValueExpression),
            AssignExpr.Operator.ASSIGN
        )
    )

    open fun renderBinaryConditionStatement(stmt: UTestBinaryConditionStatement): Statement = TODO()
    open fun renderSetFieldStatement(stmt: UTestSetFieldStatement): Statement = ExpressionStmt(
        AssignExpr(
            FieldAccessExpr(renderExpression(stmt.instance), stmt.field.name),
            renderExpression(stmt.value),
            AssignExpr.Operator.ASSIGN
        )
    )

    open fun renderSetStaticFieldStatement(stmt: UTestSetStaticFieldStatement): Statement = ExpressionStmt(
        AssignExpr(
            FieldAccessExpr(TypeExpr(JcTestTypeRenderer.render(stmt.field.enclosingClass.toType())), stmt.field.name),
            renderExpression(stmt.value),
            AssignExpr.Operator.ASSIGN
        )
    )

    open fun renderArithmeticExpression(expr: UTestArithmeticExpression): Expression = BinaryExpr(
        renderExpression(expr.lhv), renderExpression(expr.rhv), when (expr.operationType) {
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
    )

    open fun renderArrayGetExpression(expr: UTestArrayGetExpression): Expression =
        ArrayAccessExpr(renderExpression(expr.arrayInstance), renderExpression(expr.index))

    open fun renderArrayLengthExpression(expr: UTestArrayLengthExpression): Expression =
        FieldAccessExpr(renderExpression(expr.arrayInstance), "length")

    open fun renderBinaryConditionExpression(expr: UTestBinaryConditionExpression): Expression = TODO()

    open fun renderAllocateMemoryCall(expr: UTestAllocateMemoryCall): Expression = error("cannot use unsafe")

    open fun renderConstructorCall(expr: UTestConstructorCall): Expression {
        val type = JcTestTypeRenderer.render(expr.type) as ClassOrInterfaceType
        val scope = renderConstructorScope(expr)
        val renderedType = if (scope != null) parseClassOrInterface(type.name.asString()) else type
        val firstArgIsInScope = if ((scope != null) && !(expr.type as JcClassType).isStatic) 1 else 0
        return ObjectCreationExpr(scope, renderedType, NodeList(expr.args.drop(firstArgIsInScope).map { renderExpression(it) }))
    }

    private fun renderConstructorScope(expr: UTestConstructorCall): Expression? {
        val type = expr.type
        if (type !is JcClassType || type.outerType == null) return null
        return if (type.isStatic) TypeExpr(JcTestTypeRenderer.render(type.outerType!!)) else renderExpression(expr.args.first())
    }

    open fun renderMethodCall(expr: UTestMethodCall): Expression = MethodCallExpr(
        renderExpression(expr.instance),
        expr.method.name,
        NodeList(expr.args.map { renderExpression(it) })
    )

    open fun renderStaticMethodCall(expr: UTestStaticMethodCall): Expression = MethodCallExpr(
        TypeExpr(JcTestTypeRenderer.render(expr.method.enclosingClass.toType())),
        expr.method.name,
        NodeList(expr.args.map { renderExpression(it) })
    )

    open fun renderCastExpression(expr: UTestCastExpression): Expression = CastExpr(
        JcTestTypeRenderer.render(expr.type),
        renderExpression(expr.expr)
    )

    open fun renderClassExpression(expr: UTestClassExpression): Expression =
        ClassExpr(JcTestTypeRenderer.render(expr.type, false))

    open fun renderBooleanExpression(expr: UTestBooleanExpression): Expression = BooleanLiteralExpr(expr.value)
    open fun renderByteExpression(expr: UTestByteExpression): Expression = IntegerLiteralExpr(expr.value.toString())
    open fun renderCharExpression(expr: UTestCharExpression): Expression = CharLiteralExpr(expr.value)
    open fun renderDoubleExpression(expr: UTestDoubleExpression): Expression = DoubleLiteralExpr(expr.value)
    open fun renderFloatExpression(expr: UTestFloatExpression): Expression =
        DoubleLiteralExpr(expr.value.toDouble().toString() + "f")

    open fun renderIntExpression(expr: UTestIntExpression): Expression = IntegerLiteralExpr(expr.value.toString())
    open fun renderLongExpression(expr: UTestLongExpression): Expression = LongLiteralExpr(expr.value.toString())
    open fun renderNullExpression(expr: UTestNullExpression): Expression = NullLiteralExpr()
    open fun renderShortExpression(expr: UTestShortExpression): Expression = IntegerLiteralExpr(expr.value.toString())
    open fun renderStringExpression(expr: UTestStringExpression): Expression = StringLiteralExpr(expr.value)
    open fun renderCreateArrayExpression(expr: UTestCreateArrayExpression): Expression =
        ArrayCreationExpr(
            JcTestTypeRenderer.render(expr.elementType),
            NodeList(ArrayCreationLevel(renderExpression(expr.size))),
            null
        )

    open fun renderGetFieldExpression(expr: UTestGetFieldExpression): Expression = FieldAccessExpr(
        renderExpression(expr.instance),
        expr.field.name
    )

    open fun renderGetStaticFieldExpression(expr: UTestGetStaticFieldExpression): Expression = FieldAccessExpr(
        TypeExpr(JcTestTypeRenderer.render(expr.field.enclosingClass.toType())),
        expr.field.name
    )

    open fun renderGlobalMock(expr: UTestGlobalMock): Expression = TODO()

    open fun renderMockObject(expr: UTestMockObject): Expression {
        importManager.tryAdd("org.mockito.Mockito")
        val className = JcTestTypeRenderer.render(expr.type, false)
        val classExpr = ClassExpr(className)
        val mockitoClass = StaticJavaParser.parseClassOrInterfaceType("Mockito")
        val spyCreationExpression = MethodCallExpr(
            TypeExpr(mockitoClass),
            "spy",
            NodeList(listOf(classExpr))
        )
        val emptyFields = expr.fields.isEmpty()
        val emptyMethods = expr.methods.isEmpty()
        if (emptyFields && emptyMethods)
            return spyCreationExpression

        if (!emptyFields) {

        }
        TODO()
    }
}
