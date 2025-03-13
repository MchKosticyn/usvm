package org.usvm.jvm.rendering.testRenderer

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.SimpleName
import java.util.Collections
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.baseRenderer.JcMethodRenderer
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestConstExpression
import org.usvm.test.api.UTestExpression
import java.util.IdentityHashMap
import org.jacodb.api.jvm.JcClasspath

open class JcTestRenderer(
    private val test: UTest,
    override val classRenderer: JcTestClassRenderer,
    importManager: JcImportManager,
    identifiersManager: JcIdentifiersManager,
    cp: JcClasspath,
    name: SimpleName,
    testAnnotation: AnnotationExpr,
): JcMethodRenderer(
    importManager,
    identifiersManager,
    cp,
    classRenderer,
    name,
    NodeList(),
    NodeList(testAnnotation),
    NodeList(),
    voidType
) {

    protected val shouldDeclareVar: MutableSet<UTestExpression> = Collections.newSetFromMap(IdentityHashMap())

    internal val trailingExpressions: MutableList<Expression> = mutableListOf()

    override val body: JcTestBlockRenderer = JcTestBlockRenderer(
        this,
        importManager,
        JcIdentifiersManager(identifiersManager),
        cp,
        shouldDeclareVar
    )

    open fun requireVarDeclarationOf(expr: UTestExpression): Boolean = false

    open fun preventVarDeclarationOf(expr: UTestExpression): Boolean = expr is UTestConstExpression<*>

    inner class JcExprUsageVisitor: JcTestVisitor() {

        private val exprCache: MutableSet<UTestExpression> = Collections.newSetFromMap(IdentityHashMap())

        override fun visit(expr: UTestExpression) {
            if (!preventVarDeclarationOf(expr) && !exprCache.add(expr) || requireVarDeclarationOf(expr))
                // Multiple usage of expression
                shouldDeclareVar.add(expr)

            super.visit(expr)
        }
    }

    init {
        JcExprUsageVisitor().visit(test)
    }

    override fun renderInternal(): MethodDeclaration {
        for (inst in test.initStatements)
            body.renderInst(inst)

        body.renderInst(test.callMethodExpression)

        trailingExpressions.forEach { expr -> body.addExpression(expr) }

        return super.renderInternal()
    }
}
