package org.usvm.jvm.rendering.testRenderer

import com.github.javaparser.ast.ArrayCreationLevel
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.ArrayAccessExpr
import com.github.javaparser.ast.expr.ArrayCreationExpr
import com.github.javaparser.ast.expr.BinaryExpr
import com.github.javaparser.ast.expr.BooleanLiteralExpr
import com.github.javaparser.ast.expr.CastExpr
import com.github.javaparser.ast.expr.CharLiteralExpr
import com.github.javaparser.ast.expr.DoubleLiteralExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.IntegerLiteralExpr
import com.github.javaparser.ast.expr.LongLiteralExpr
import com.github.javaparser.ast.expr.NullLiteralExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.expr.TypeExpr
import com.github.javaparser.ast.type.ReferenceType
import org.jacodb.api.jvm.JcClassType
import org.usvm.jvm.rendering.baseRenderer.JcBlockRenderer
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.test.api.ArithmeticOperationType
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
import org.jacodb.api.jvm.ext.toType

open class JcTestBlockRenderer private constructor(
    importManager: JcImportManager,
    private val shouldDeclareVar: IdentityHashMap<UTestExpression, Unit>,
    private val exprCache: IdentityHashMap<UTestExpression, Expression>,
    thrownExceptions: HashSet<ReferenceType>
) : JcBlockRenderer(importManager, thrownExceptions) {

    constructor(
        importManager: JcImportManager,
        shouldDeclareVar: IdentityHashMap<UTestExpression, Unit>
    ) : this(importManager, shouldDeclareVar, IdentityHashMap(), HashSet())

    override fun newInnerBlock(): JcTestBlockRenderer {
        return JcTestBlockRenderer(
            importManager,
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
            if (shouldDeclareVar.containsKey(expr))
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
        TODO()
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

    open fun renderBinaryConditionExpression(expr: UTestBinaryConditionExpression): Expression = TODO()

    open fun renderAllocateMemoryCall(expr: UTestAllocateMemoryCall): Expression = error("Unsafe is not supported")

    open fun renderConstructorCall(expr: UTestConstructorCall): Expression {
        val type = expr.type as JcClassType
        return renderConstructorCall(expr.method, type, expr.args.map { renderExpression(it) })
    }

    open fun renderMethodCall(expr: UTestMethodCall): Expression {
        return renderMethodCall(
            expr.method,
            renderExpression(expr.instance),
            expr.args.map { renderExpression(it) }
        )
    }

    open fun renderStaticMethodCall(expr: UTestStaticMethodCall): Expression {
        return renderStaticMethodCall(expr.method, expr.args.map { renderExpression(it) })
    }

    open fun renderCastExpression(expr: UTestCastExpression): Expression = CastExpr(
        renderType(expr.type),
        renderExpression(expr.expr)
    )

    open fun renderClassExpression(expr: UTestClassExpression): Expression =
        renderClassExpression(expr.type as JcClassType)

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
            renderType(expr.elementType),
            NodeList(ArrayCreationLevel(renderExpression(expr.size))),
            null
        )

    open fun renderGetFieldExpression(expr: UTestGetFieldExpression): Expression = FieldAccessExpr(
        renderExpression(expr.instance),
        expr.field.name
    )

    open fun renderGetStaticFieldExpression(expr: UTestGetStaticFieldExpression): Expression = FieldAccessExpr(
        TypeExpr(renderClass(expr.field.enclosingClass.toType())),
        expr.field.name
    )

    open fun renderGlobalMock(expr: UTestGlobalMock): Expression = TODO("global mock is not implemented")

    open fun renderMockObject(expr: UTestMockObject): Expression {
        val type = expr.type as JcClassType
        val spyCreationExpression = mockitoSpyMethodCall(type)
        val emptyFields = expr.fields.isEmpty()
        val emptyMethods = expr.methods.isEmpty()
        if (emptyFields && emptyMethods)
            return spyCreationExpression

        val varExpr = renderVarDeclaration(type, spyCreationExpression)

        for ((field, fieldValue) in expr.fields) {
            val renderedFieldValue = renderExpression(fieldValue)
            renderSetFieldStatement(varExpr, field, renderedFieldValue)
        }

        for ((method, mockValues) in expr.methods) {
            if (mockValues.isEmpty())
                continue

//            val
//            for ()
//            val renderedMockValues = renderExpression(mockValue)

        }
        if (!emptyMethods) {
            // TODO: ...
        }

        return varExpr
    }
}
