package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.ArrayAccessExpr
import com.github.javaparser.ast.expr.AssignExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.TypeExpr
import com.github.javaparser.ast.expr.VariableDeclarationExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.Statement
import com.github.javaparser.ast.type.ReferenceType
import org.jacodb.api.jvm.JcType
import com.github.javaparser.ast.type.Type
import org.jacodb.api.jvm.JcMethod

open class JcBlockRenderer protected constructor(
    importManager: JcImportManager,
    protected val thrownExceptions: HashSet<ReferenceType>
) : JcCodeRenderer<BlockStmt>(importManager) {

    constructor(importManager: JcImportManager): this(importManager, HashSet())

    private val statements = NodeList<Statement>()

    override fun renderInternal(): BlockStmt {
        return BlockStmt(statements)
    }

    fun getThrownExceptions(): NodeList<ReferenceType> {
        return NodeList(thrownExceptions)
    }

    open fun newInnerBlock(): JcBlockRenderer {
        return JcBlockRenderer(importManager, thrownExceptions)
    }

    fun addExpression(expr: Expression) {
        statements.add(ExpressionStmt(expr))
    }

    fun renderVarDeclaration(type: JcType, expr: Expression, name: String? = null): NameExpr {
        val renderedType = renderType(type)
        // TODO
        val name = "keke"
        val declarator = VariableDeclarator(renderedType, name, expr)
        addExpression(VariableDeclarationExpr(declarator))
        return NameExpr(name)
    }

    fun renderArraySetStatement(array: Expression, index: Expression, value: Expression) {
        addExpression(
            AssignExpr(
                ArrayAccessExpr(array, index),
                value,
                AssignExpr.Operator.ASSIGN
            )
        )
    }

    fun renderSetFieldStatement(instance: Expression, fieldName: String, value: Expression) {
        addExpression(
            AssignExpr(
                FieldAccessExpr(instance, fieldName),
                value,
                AssignExpr.Operator.ASSIGN
            )
        )
    }

    fun renderSetStaticFieldStatement(type: Type, fieldName: String, value: Expression) {
        addExpression(
            AssignExpr(
                FieldAccessExpr(TypeExpr(type), fieldName),
                value,
                AssignExpr.Operator.ASSIGN
            )
        )
    }

    protected fun addThrownExceptions(method: JcMethod) {
        thrownExceptions.addAll(
            method.exceptions.map {
                StaticJavaParser.parseClassOrInterfaceType(qualifiedName(it))
            }
        )
    }

    fun renderMethodCall(method: JcMethod, instance: Expression, args: List<Expression>): MethodCallExpr {
        addThrownExceptions(method)

        return MethodCallExpr(
            instance,
            method.name,
            NodeList(args)
        )
    }

    fun renderStaticMethodCall(method: JcMethod, args: List<Expression>): MethodCallExpr {
        addThrownExceptions(method)

        return MethodCallExpr(
            TypeExpr(renderClass(method.enclosingClass)),
            method.name,
            NodeList(args)
        )
    }
}
