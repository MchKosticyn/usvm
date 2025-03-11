package org.usvm.jvm.rendering.testRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.usvm.jvm.rendering.baseRenderer.JcClassRenderer
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.test.api.UTest

class JcTestClassRenderer : JcClassRenderer {

    constructor(
        name: String
    ) : super(JcImportManager(), name)

    constructor(
        importManager: JcImportManager,
        decl: ClassOrInterfaceDeclaration
    ) : super(importManager, decl)

    private val testAnnotation: AnnotationExpr = testAnnotationJUnit

    fun addTest(test: UTest, name: SimpleName): JcTestRenderer {
        val renderer = JcTestRenderer(test, this, importManager, name, testAnnotation)
        addRenderingMethod(renderer)
        return renderer
    }
}
