package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestRenderer
import org.usvm.test.api.UTest

open class JcSpringMvcTestRenderer(
    test: UTest,
    override val classRenderer: JcSpringMvcTestClassRenderer,
    importManager: JcUnsafeImportManager,
    identifiersManager: JcIdentifiersManager,
    cp: JcClasspath,
    name: SimpleName,
    testAnnotation: AnnotationExpr,
    mvcTestClass: JcClassOrInterface
): JcSpringUnitTestRenderer(
    test,
    classRenderer,
    importManager,
    identifiersManager,
    cp,
    name,
    testAnnotation,
) {

    override val body: JcSpringMvcTestBlockRenderer = JcSpringMvcTestBlockRenderer(
        this,
        importManager,
        JcIdentifiersManager(identifiersManager),
        cp,
        shouldDeclareVar,
        mvcTestClass
    )
}
