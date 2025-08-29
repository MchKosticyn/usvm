package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.spring.JcSpringReflectionUtilsRenderer
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestClassRenderer
import org.usvm.test.api.UTest

open class JcSpringUnitTestClassRenderer : JcUnsafeTestClassRenderer {

    constructor(
        name: String,
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        reflectionUtilsRenderer: JcSpringReflectionUtilsRenderer
    ) : super(name, importManager, identifiersManager, cp, reflectionUtilsRenderer)

    constructor(
        decl: ClassOrInterfaceDeclaration,
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        reflectionUtilsRenderer: JcSpringReflectionUtilsRenderer
    ) : super(decl, importManager, identifiersManager, cp, reflectionUtilsRenderer)


    override fun createTestRenderer(
        test: UTest,
        identifiersManager: JcIdentifiersManager,
        name: SimpleName,
        annotations: List<AnnotationExpr>,
    ): JcTestRenderer {
        return JcSpringUnitTestRenderer(
            test,
            this,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            name,
            annotations,
            unsafeUtilsRenderer as JcSpringReflectionUtilsRenderer
        )
    }
}
