package org.usvm.jvm.rendering

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.VariableDeclarationExpr
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.Statement
import org.jacodb.api.jvm.JcType

open class JcBlockRenderer(
    importManager: JcImportManager
): JcCodeRenderer<BlockStmt>(importManager) {
    private val statements = NodeList<Statement>()

    override fun renderInternal(): BlockStmt {
        return BlockStmt(statements)
    }

    open fun newInnerBlock(): JcBlockRenderer {
        return JcBlockRenderer(importManager)
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
}
