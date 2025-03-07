package org.usvm.jvm.rendering

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName

class JcTestClassRenderer : JcClassRenderer {

    constructor(
        name: String
    ) : super(JcImportManager(), name)

    constructor(
        importManager: JcImportManager,
        decl: ClassOrInterfaceDeclaration
    ) : super(importManager, decl)

    private val testAnnotation: AnnotationExpr = testAnnotationJUnit

    fun addTest(name: SimpleName): JcTestRenderer {
        val renderer = JcTestRenderer(importManager, name, testAnnotation)
        addRenderingMethod(renderer)
        return renderer
    }
}
