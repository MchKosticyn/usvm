package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestClassRenderer
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.test.api.UTest

open class JcUnsafeTestClassRenderer : JcTestClassRenderer {

    override val importManager: JcUnsafeImportManager
        get() = super.importManager as JcUnsafeImportManager

    constructor(
        name: String,
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath
    ) : super(name, importManager, identifiersManager, cp)

    constructor(
        decl: ClassOrInterfaceDeclaration,
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath
    ) : super(decl, importManager, identifiersManager, cp)

    override fun createTestRenderer(
        test: UTest,
        identifiersManager: JcIdentifiersManager,
        name: SimpleName,
        testAnnotation: AnnotationExpr,
    ): JcTestRenderer {
        return JcUnsafeTestRenderer(
            test,
            this,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            name,
            testAnnotation
        )
    }
}
