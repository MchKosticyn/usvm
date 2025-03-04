package org.usvm.jvm.rendering.testRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.MarkerAnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.usvm.jvm.rendering.baseRenderer.JcClassRenderer
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.test.api.UTest

open class JcTestClassRenderer : JcClassRenderer {

    protected constructor(
        importManager: JcImportManager,
        name: String
    ) : super(importManager, name)

    constructor(
        name: String
    ) : super(name)

    protected constructor(
        importManager: JcImportManager,
        decl: ClassOrInterfaceDeclaration
    ): super(importManager, decl)

    constructor(
        decl: ClassOrInterfaceDeclaration
    ) : super(decl)

    protected val testAnnotationJUnit: AnnotationExpr by lazy {
        importManager.add("org.junit.jupiter.api.Test")
        MarkerAnnotationExpr("Test")
    }

    protected open fun createTestRenderer(
        test: UTest,
        identifiersManager: JcIdentifiersManager,
        name: SimpleName,
        testAnnotation: AnnotationExpr,
    ): JcTestRenderer {
        return JcTestRenderer(
            test,
            this,
            importManager,
            identifiersManager,
            name,
            testAnnotation
        )
    }

    fun addTest(test: UTest, namePrefix: String? = null): JcTestRenderer {
        val renderer = createTestRenderer(
            test,
            JcIdentifiersManager(identifiersManager),
            identifiersManager[namePrefix ?: "test"],
            testAnnotationJUnit
        )

        addRenderingMethod(renderer)
        return renderer
    }
}
