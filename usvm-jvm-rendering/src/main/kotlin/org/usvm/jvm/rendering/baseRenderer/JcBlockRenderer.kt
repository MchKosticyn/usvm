package org.usvm.jvm.rendering.baseRenderer

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
import com.github.javaparser.ast.type.Type
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType

open class JcBlockRenderer protected constructor(
    protected open val methodRenderer: JcMethodRenderer,
    importManager: JcImportManager,
    identifiersManager: JcIdentifiersManager,
    cp: JcClasspath,
    protected val thrownExceptions: HashSet<ReferenceType>
) : JcCodeRenderer<BlockStmt>(importManager, identifiersManager, cp) {

    constructor(
        methodRenderer: JcMethodRenderer,
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath
    ) : this(methodRenderer, importManager, identifiersManager, cp, HashSet())

    private val statements = NodeList<Statement>()

    protected open val classRenderer get() = methodRenderer.classRenderer

    override fun renderInternal(): BlockStmt {
        return BlockStmt(statements)
    }

    fun getThrownExceptions(): NodeList<ReferenceType> {
        return NodeList(thrownExceptions)
    }

    open fun newInnerBlock(): JcBlockRenderer {
        return JcBlockRenderer(
            methodRenderer,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            thrownExceptions
        )
    }

    fun addExpression(expr: Expression) {
        statements.add(ExpressionStmt(expr))
    }

    fun renderVarDeclaration(type: JcType, expr: Expression? = null, namePrefix: String? = null): NameExpr {
        val renderedType = renderType(type)
        return renderVarDeclaration(renderedType, expr, namePrefix)
    }

    protected fun renderVarDeclaration(type: Type, expr: Expression? = null, namePrefix: String? = null): NameExpr {
        val name = identifiersManager[namePrefix ?: "v"]
        val declarator = VariableDeclarator(type, name, expr)
        addExpression(VariableDeclarationExpr(declarator))
        return NameExpr(name)
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
        val cp = method.enclosingClass.classpath
        thrownExceptions.addAll(
            method.exceptions.map {
                renderClass(it.typeName)
            }
        )
    }

    protected fun addThrownException(typeName: String, cp: JcClasspath) {
        var thrown = renderClass(typeName)
        thrownExceptions.add(thrown)
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
