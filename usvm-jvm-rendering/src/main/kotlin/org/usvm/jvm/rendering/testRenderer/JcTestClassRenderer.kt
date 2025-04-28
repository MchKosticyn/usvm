package org.usvm.jvm.rendering.testRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.MarkerAnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcClassRenderer
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.test.api.UTest

open class JcTestClassRenderer : JcClassRenderer {

    constructor(
        name: String,
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath
    ) : super(name, importManager, identifiersManager, cp)

    constructor(
        decl: ClassOrInterfaceDeclaration,
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
    ): super(decl, importManager, identifiersManager, cp)

    protected val testAnnotationJUnit: AnnotationExpr by lazy {
        val annotationName = renderClass("org.junit.jupiter.api.Test")
        MarkerAnnotationExpr(annotationName.nameWithScope)
    }

    protected open fun createTestRenderer(
        test: UTest,
        testInfo: JcTestInfo,
        identifiersManager: JcIdentifiersManager,
        name: SimpleName,
        testAnnotation: AnnotationExpr,
    ): JcTestRenderer {
        return JcTestRenderer(
            test,
            this,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            name,
            testAnnotation
        )
    }

    fun addTest(test: UTest, testInfo: JcTestInfo): JcTestRenderer {
        val renderer = createTestRenderer(
            test,
            testInfo,
            JcIdentifiersManager(identifiersManager),
            identifiersManager[testInfo.testNamePrefix],
            testAnnotationJUnit
        )

        addRenderingMethod(renderer)
        return renderer
    }
}
