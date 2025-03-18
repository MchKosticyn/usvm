package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestRenderer
import org.usvm.test.api.UTest

open class JcSpringUnitTestRenderer(
    test: UTest,
    classRenderer: JcSpringUnitTestClassRenderer,
    importManager: JcUnsafeImportManager,
    identifiersManager: JcIdentifiersManager,
    name: SimpleName,
    testAnnotation: AnnotationExpr,
): JcUnsafeTestRenderer(
    test,
    classRenderer,
    importManager,
    identifiersManager,
    name,
    testAnnotation,
) {

    override val body: JcSpringUnitTestBlockRenderer = JcSpringUnitTestBlockRenderer(
        this,
        importManager,
        JcIdentifiersManager(identifiersManager),
        shouldDeclareVar
    )
}
