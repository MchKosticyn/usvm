package org.usvm.jvm.rendering.testRenderer

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import java.util.Collections
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.baseRenderer.JcMethodRenderer
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestConstExpression
import org.usvm.test.api.UTestExpression
import java.util.IdentityHashMap

open class JcTestRenderer(
    private val test: UTest,
    override val classRenderer: JcTestClassRenderer,
    importManager: JcImportManager,
    identifiersManager: JcIdentifiersManager,
    name: SimpleName,
    testAnnotation: AnnotationExpr,
): JcMethodRenderer(
    importManager,
    identifiersManager,
    classRenderer,
    name,
    NodeList(),
    NodeList(testAnnotation),
    NodeList(),
    voidType
) {

    private val shouldDeclareVar: IdentityHashMap<UTestExpression, Unit> = IdentityHashMap()

    override val body: JcTestBlockRenderer = JcTestBlockRenderer(importManager, JcIdentifiersManager(identifiersManager), shouldDeclareVar)

    open fun requireVarDeclarationOf(expr: UTestExpression): Boolean = false

    inner class JcExprUsageVisitor: JcTestVisitor() {

        private val exprCache: MutableSet<UTestExpression> = Collections.newSetFromMap(IdentityHashMap())

        override fun visit(expr: UTestExpression) {
            if (expr !is UTestConstExpression<*> && !exprCache.add(expr) || requireVarDeclarationOf(expr))
                // Multiple usage of expression
                shouldDeclareVar[expr] = Unit

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

        return super.renderInternal()
    }
}
