package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.testRenderer.JcTestClassRenderer
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.test.api.UTest

open class JcUnsafeTestClassRenderer : JcTestClassRenderer {

    val unsafeUtilsRenderer: JcUnsafeUtilsRenderer

    constructor(
        name: String,
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        unsafeUtilsRenderer: JcUnsafeUtilsRenderer
    ) : super(name, importManager, identifiersManager, cp) {
        this.unsafeUtilsRenderer = unsafeUtilsRenderer
    }

    constructor(
        decl: ClassOrInterfaceDeclaration,
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        unsafeUtilsRenderer: JcUnsafeUtilsRenderer
    ) : super(decl, importManager, identifiersManager, cp) {
        this.unsafeUtilsRenderer = unsafeUtilsRenderer
    }

    override fun createTestRenderer(
        test: UTest,
        identifiersManager: JcIdentifiersManager,
        name: SimpleName,
        annotations: List<AnnotationExpr>,
    ): JcTestRenderer {

        return JcUnsafeTestRenderer(
            test,
            this,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            name,
            annotations,
            unsafeUtilsRenderer
        )
    }
}
