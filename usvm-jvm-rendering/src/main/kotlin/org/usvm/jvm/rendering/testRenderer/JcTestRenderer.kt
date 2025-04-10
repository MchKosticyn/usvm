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
import org.usvm.test.api.UTestInst

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

        private fun shouldDeclareVarCheck(expr: UTestExpression): Boolean {
            return !preventVarDeclarationOf(expr) && cache.contains(expr) || requireVarDeclarationOf(expr)
        }

        override fun visit(inst: UTestInst) {
            if (inst is UTestExpression && shouldDeclareVarCheck(inst))
                shouldDeclareVar.add(inst)

            super.visit(inst)
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
