package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.VariableDeclarationExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.Statement
import com.github.javaparser.ast.type.ReferenceType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType

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
        addExpression(renderArraySet(array, index, value))
    }

    fun renderSetFieldStatement(instance: Expression, field: JcField, value: Expression) {
        addExpression(renderSetField(instance, field, value))
    }

    fun renderSetStaticFieldStatement(field: JcField, value: Expression) {
        addExpression(renderSetStaticField(field, value))
    }

    protected fun addThrownExceptions(method: JcMethod) {
        thrownExceptions.addAll(
            method.exceptions.map {
                StaticJavaParser.parseClassOrInterfaceType(qualifiedName(it.typeName))
            }
        )
    }

    override fun renderConstructorCall(ctor: JcMethod, type: JcClassType, args: List<Expression>): Expression {
        addThrownExceptions(ctor)
        return super.renderConstructorCall(ctor, type, args)
    }

    override fun renderMethodCall(method: JcMethod, instance: Expression, args: List<Expression>): Expression {
        addThrownExceptions(method)
        return super.renderMethodCall(method, instance, args)
    }

    override fun renderStaticMethodCall(method: JcMethod, args: List<Expression>): Expression {
        addThrownExceptions(method)
        return super.renderStaticMethodCall(method, args)
    }
}
