package org.usvm.jvm.rendering

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.CastExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MethodCallExpr
import com.github.javaparser.ast.expr.NullLiteralExpr
import com.github.javaparser.ast.expr.StringLiteralExpr
import com.github.javaparser.ast.expr.TypeExpr
import com.github.javaparser.ast.stmt.ExpressionStmt
import com.github.javaparser.ast.stmt.Statement
import java.util.IdentityHashMap
import org.jacodb.api.jvm.ext.toType
import org.usvm.jvm.rendering.Utils.parseClassOrInterface
import org.usvm.jvm.rendering.Utils.qualifiedName
import org.usvm.jvm.rendering.Utils.tryAddThrownException
import org.usvm.test.api.*

class JcTestRendererImpl(classDeclaration: ClassOrInterfaceDeclaration, methodDeclaration: MethodDeclaration, importManager: JcTestImportManager) : JcTestRenderer(classDeclaration, methodDeclaration, importManager) {
    private val noRenderPool = mutableListOf<UTestInst>()
    override fun beforeRendering(test: UTest): UTest {
        val testInst = test.initStatements + test.callMethodExpression
        val forcedCtorCalls = mutableMapOf<UTestMethodCall, Int>()
        testInst.forEachIndexed { index, inst ->
            UTestInstTraverser.traverseInst(inst) { e, _ ->
                if (e is UTestMethodCall && e.method.isConstructor) forcedCtorCalls.put(
                    e,
                    index
                )
            }
        }
        noRenderPool.addAll(forcedCtorCalls.keys.map { it.instance })
        val filteredInst = test.initStatements.filterIndexed { index, inst ->
            var shouldBeRemoved = false
            UTestInstTraverser.traverseInst(inst) { e, i -> if ((forcedCtorCalls[e] ?: 0) > index) {
                shouldBeRemoved = true
                return@traverseInst
            } }
            shouldBeRemoved
        }
        return UTest(filteredInst, test.callMethodExpression)
    }
    override fun renderAllocateMemoryCall(expr: UTestAllocateMemoryCall): Expression {
        methodDeclaration.tryAddThrownException(parseClassOrInterface("java.lang.InstantiationException"))
        return CastExpr(
            parseClassOrInterface(qualifiedName(expr.clazz)),
            MethodCallExpr(
                TypeExpr(parseClassOrInterface("org.usvm.jvm.rendering.ReflectionUtils")), "allocateInstance"
            ).apply { arguments = NodeList(renderExpression(UTestClassExpression(expr.clazz.toType()))) }
        )
    }

    override fun renderMethodCall(expr: UTestMethodCall): Expression {
        if (expr.method.isConstructor) {
            return super.renderConstructorCall(UTestConstructorCall(expr.method, expr.args))
        }
        return super.renderMethodCall(expr)
    }

    override fun requireDeclarationOf(expr: UTestExpression): Boolean {
        return expr is UTestAllocateMemoryCall && expr !in noRenderPool
    }
}

class JcSpringTestRendererImpl(
    cu: CompilationUnit,
    classDeclaration: ClassOrInterfaceDeclaration,
    methodDeclaration: MethodDeclaration,
    importManager: JcTestImportManager
) : JcTestRenderer(classDeclaration, methodDeclaration, importManager) {
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