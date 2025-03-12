package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestClassRenderer
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.test.api.UTest

class JcUnsafeTestClassRenderer : JcTestClassRenderer {

    constructor(
        name: String,
        reflectionUtilsFullName: String
    ) : super(JcUnsafeImportManager(reflectionUtilsFullName), name)

    constructor(
        decl: ClassOrInterfaceDeclaration,
        reflectionUtilsFullName: String
    ) : super(JcUnsafeImportManager(reflectionUtilsFullName), decl)

    override fun createTestRenderer(
        test: UTest,
        identifiersManager: JcIdentifiersManager,
        name: SimpleName,
        testAnnotation: AnnotationExpr,
    ): JcTestRenderer {
        return JcUnsafeTestRenderer(
            test,
            this,
            importManager as JcUnsafeImportManager,
            identifiersManager,
            name,
            testAnnotation
        )
    }
}
