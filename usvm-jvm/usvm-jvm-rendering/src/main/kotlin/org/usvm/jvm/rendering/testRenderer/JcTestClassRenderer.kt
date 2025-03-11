package org.usvm.jvm.rendering.testRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import org.usvm.jvm.rendering.baseRenderer.JcClassRenderer
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.test.api.UTest

class JcTestClassRenderer : JcClassRenderer {

    constructor(
        name: String
    ) : super(name)

    constructor(
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        decl: ClassOrInterfaceDeclaration
    ) : super(importManager, identifiersManager, decl)

    private val testAnnotation: AnnotationExpr = testAnnotationJUnit

    fun addTest(test: UTest, namePrefix: String? = null): JcTestRenderer {
        val renderer = JcTestRenderer(
            test,
            this,
            importManager,
            JcIdentifiersManager(identifiersManager),
            identifiersManager[namePrefix ?: "test"],
            testAnnotation
        )
        addRenderingMethod(renderer)
        return renderer
    }
}
