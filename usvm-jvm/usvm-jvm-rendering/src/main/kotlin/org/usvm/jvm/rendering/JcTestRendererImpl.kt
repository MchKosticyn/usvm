package org.usvm.jvm.rendering

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.CastExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NullLiteralExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.expr.TypeExpr
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.Statement
import org.jacodb.api.jvm.ext.toType
import org.usvm.jvm.rendering.Utils.parseClassOrInterface
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestClassExpression
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestGetFieldExpression
import org.usvm.test.api.UTestGetStaticFieldExpression
import org.usvm.test.api.UTestSetFieldStatement
import org.usvm.test.api.UTestSetStaticFieldStatement

class JcTestRendererImpl(cu: CompilationUnit, importManager: JcTestImportManager) : JcTestRenderer(importManager) {
    override fun renderAllocateMemoryCall(expr: UTestAllocateMemoryCall): Expression {
        return MethodCallExpr(
            TypeExpr(parseClassOrInterface("org.usvm.jvm.rendering.ReflectionUtils")),
            "allocateInstance"
        ).apply {
            this.arguments = NodeList(renderExpression(UTestClassExpression(expr.clazz.toType())))
        }
    }

    override fun requireDeclarationOf(expr: UTestExpression): Boolean {
        return expr is UTestAllocateMemoryCall
    }
}

class JcSpringTestRendererImpl(
    private val methodDeclaration: MethodDeclaration,
    cu: CompilationUnit,
    importManager: JcTestImportManager
) : JcTestRenderer(importManager) {
    override fun renderAllocateMemoryCall(expr: UTestAllocateMemoryCall): Expression {
        if (expr.clazz.isStatic) return TypeExpr(parseClassOrInterface(expr.clazz.toType().typeName))
        importManager.tryAdd("org.usvm.jvm.rendering.ReflectionUtils")
        return CastExpr(parseClassOrInterface(expr.clazz.toType().typeName), MethodCallExpr(
            TypeExpr(parseClassOrInterface("org.usvm.jvm.rendering.ReflectionUtils")),
            "allocateInstance"
        ).apply {
            this.arguments = NodeList(renderExpression(UTestClassExpression(expr.clazz.toType())))
        })
    }

    override fun renderGetFieldExpression(expr: UTestGetFieldExpression): Expression {
        if (!expr.field.isPublic) {
            importManager.tryAdd("org.springframework.test.util.ReflectionTestUtils")
            return MethodCallExpr(
                TypeExpr(parseClassOrInterface("org.springframework.test.util.ReflectionTestUtils")),
                "getField",
                NodeList(
                    renderExpression(expr.instance),
                    TypeExpr(parseClassOrInterface(expr.instance.type!!.typeName)),
                    StringLiteralExpr(expr.field.name)
                )
            )
        }
        return super.renderGetFieldExpression(expr)
    }

    override fun renderSetFieldStatement(stmt: UTestSetFieldStatement): Statement {
        if (!stmt.field.isPublic || stmt.field.isFinal) {
            importManager.tryAdd("org.springframework.test.util.ReflectionTestUtils")
            return ExpressionStmt(
                MethodCallExpr(
                    TypeExpr(parseClassOrInterface("org.springframework.test.util.ReflectionTestUtils")),
                    "setField",
                    NodeList(
                        renderExpression(stmt.instance),
                        StringLiteralExpr(stmt.field.name),
                        renderExpression(stmt.value)
                    )
                )
            )
        }
        return super.renderSetFieldStatement(stmt)
    }

    override fun renderGetStaticFieldExpression(expr: UTestGetStaticFieldExpression): Expression {
        if (!expr.field.isPublic) {
            importManager.tryAdd("org.springframework.test.util.ReflectionTestUtils")
            val parsedField = parseClassOrInterface(expr.field.enclosingClass.name)
            return MethodCallExpr(
                TypeExpr(parseClassOrInterface("org.springframework.test.util.ReflectionTestUtils")),
                "getField",
                NodeList(
                    NullLiteralExpr(),
                    TypeExpr(parsedField),
                    StringLiteralExpr(expr.field.name)
                )
            )
        }
        return super.renderGetStaticFieldExpression(expr)
    }

    override fun renderSetStaticFieldStatement(stmt: UTestSetStaticFieldStatement): Statement {
        if (!stmt.field.isPublic || stmt.field.isFinal) {
            importManager.tryAdd("org.springframework.test.util.ReflectionTestUtils")
            val parsedField = parseClassOrInterface(stmt.field.enclosingClass.name)
            return ExpressionStmt(
                MethodCallExpr(
                    TypeExpr(parseClassOrInterface("org.springframework.test.util.ReflectionTestUtils")),
                    "setField",
                    NodeList(
                        TypeExpr(parsedField),
                        StringLiteralExpr(stmt.field.name),
                        renderExpression(stmt.value)
                    )
                )
            )
        }
        return super.renderSetStaticFieldStatement(stmt)
    }
}