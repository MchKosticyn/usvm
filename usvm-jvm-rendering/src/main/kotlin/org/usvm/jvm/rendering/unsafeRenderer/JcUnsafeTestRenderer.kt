package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.test.api.UTest

open class JcUnsafeTestRenderer(
    test: UTest,
    classRenderer: JcUnsafeTestClassRenderer,
    importManager: JcUnsafeImportManager,
    identifiersManager: JcIdentifiersManager,
    cp: JcClasspath,
    name: SimpleName,
    testAnnotation: AnnotationExpr,
): JcTestRenderer(
    test,
    classRenderer,
    importManager,
    identifiersManager,
    cp,
    name,
    testAnnotation,
) {

    override val body: JcUnsafeTestBlockRenderer = JcUnsafeTestBlockRenderer(
        this,
        importManager,
        JcIdentifiersManager(identifiersManager),
        cp,
        shouldDeclareVar
    )
}
