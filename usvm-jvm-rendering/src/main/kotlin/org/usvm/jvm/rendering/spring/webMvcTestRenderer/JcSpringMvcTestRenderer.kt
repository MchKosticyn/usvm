package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestRenderer
import org.usvm.test.api.UTest

open class JcSpringMvcTestRenderer(
    test: UTest,
    override val classRenderer: JcSpringMvcTestClassRenderer,
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

    override val body: JcSpringMvcTestBlockRenderer = JcSpringMvcTestBlockRenderer(
        importManager,
        JcIdentifiersManager(identifiersManager),
        shouldDeclareVar
    )
}
