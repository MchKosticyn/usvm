package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.ThisExpr
import com.github.javaparser.ast.type.ReferenceType
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestBlockRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestGetFieldExpression
import java.util.IdentityHashMap

open class JcSpringMvcTestBlockRenderer protected constructor(
    override val methodRenderer: JcSpringMvcTestRenderer,
    importManager: JcUnsafeImportManager,
    identifiersManager: JcIdentifiersManager,
    cp: JcClasspath,
    shouldDeclareVar: Set<UTestExpression>,
    exprCache: IdentityHashMap<UTestExpression, Expression>,
    thrownExceptions: HashSet<ReferenceType>,
    private val mvcTestClass: JcClassOrInterface
) : JcSpringUnitTestBlockRenderer(
    methodRenderer,
    importManager,
    identifiersManager,
    cp,
    shouldDeclareVar,
    exprCache,
    thrownExceptions
) {

    constructor(
        methodRenderer: JcSpringMvcTestRenderer,
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        shouldDeclareVar: Set<UTestExpression>,
        mvcTestClass: JcClassOrInterface
    ) : this(
        methodRenderer,
        importManager,
        identifiersManager,
        cp,
        shouldDeclareVar,
        IdentityHashMap(),
        HashSet(),
        mvcTestClass
    )

    override fun newInnerBlock(): JcSpringMvcTestBlockRenderer {
        return JcSpringMvcTestBlockRenderer(
            methodRenderer,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            shouldDeclareVar,
            IdentityHashMap(exprCache),
            thrownExceptions,
            mvcTestClass
        )
    }

    override fun renderGetFieldExpression(expr: UTestGetFieldExpression): Expression {
        val field = expr.field
        if (expr.field.enclosingClass == mvcTestClass) {
            val testClassField = classRenderer.getOrCreateField(field)
            return FieldAccessExpr(ThisExpr(), NodeList(), testClassField)
        }

        return super.renderGetFieldExpression(expr)
    }
}
