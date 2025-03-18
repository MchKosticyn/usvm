package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.test.api.UTest

open class JcUnsafeTestRenderer(
    test: UTest,
    classRenderer: JcUnsafeTestClassRenderer,
    importManager: JcUnsafeImportManager,
    identifiersManager: JcIdentifiersManager,
    name: SimpleName,
    testAnnotation: AnnotationExpr,
): JcTestRenderer(
    test,
    classRenderer,
    importManager,
    identifiersManager,
    name,
    testAnnotation,
) {

    override val body: JcUnsafeTestBlockRenderer = JcUnsafeTestBlockRenderer(
        this,
        importManager,
        JcIdentifiersManager(identifiersManager),
        shouldDeclareVar
    )
}
