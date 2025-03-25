package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.VariableDeclarationExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.IfStmt
import com.github.javaparser.ast.stmt.Statement
import com.github.javaparser.ast.type.ReferenceType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType

open class JcBlockRenderer private constructor(
    importManager: JcImportManager,
    identifiersManager: JcIdentifiersManager,
    protected val thrownExceptions: HashSet<ReferenceType>,
    private val vars: HashSet<NameExpr>
) : JcCodeRenderer<BlockStmt>(importManager, identifiersManager) {

    protected constructor(
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        thrownExceptions: HashSet<ReferenceType>
    ) : this(importManager, identifiersManager, thrownExceptions, HashSet())

    constructor(
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager
    ): this(importManager, identifiersManager, HashSet())

    private val statements = NodeList<Statement>()

    override fun renderInternal(): BlockStmt {
        return BlockStmt(statements)
    }

    fun getThrownExceptions(): NodeList<ReferenceType> {
        return NodeList(thrownExceptions)
    }

    open fun newInnerBlock(): JcBlockRenderer {
        val newIdentifiersManager = JcIdentifiersManager(identifiersManager)
        return JcBlockRenderer(importManager, newIdentifiersManager, thrownExceptions, HashSet(vars))
    }

    fun addExpression(expr: Expression) {
        statements.add(ExpressionStmt(expr))
    }

    fun renderVarDeclaration(type: JcType, expr: Expression? = null, namePrefix: String? = null): NameExpr {
        if (expr is NameExpr && vars.contains(expr))
            return expr

        val renderedType = renderType(type)
        val name = identifiersManager[namePrefix ?: "v"]
        val declarator = VariableDeclarator(renderedType, name, expr)
        addExpression(VariableDeclarationExpr(declarator))
        val varNameExpr = NameExpr(name)
        vars.add(varNameExpr)
        return varNameExpr
    }

    fun renderIfStatement(
        condition: Expression,
        initThenBody: (JcBlockRenderer) -> Unit,
        initElseBody: (JcBlockRenderer) -> Unit
    ) {
        val thenBlockRenderer = newInnerBlock()
        initThenBody(thenBlockRenderer)
        val elseBlockRenderer = newInnerBlock()
        initElseBody(elseBlockRenderer)
        val thenBlock = thenBlockRenderer.render()
        val thenStmt = thenBlock.statements.singleOrNull() ?: thenBlock
        val elseBlock = elseBlockRenderer.render()
        val elseStmt = elseBlock.statements.singleOrNull() ?: elseBlock
        statements.add(IfStmt(condition, thenStmt, elseStmt))
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

    protected fun addThrownException(name: String) {
        thrownExceptions.add(StaticJavaParser.parseClassOrInterfaceType(name))
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
