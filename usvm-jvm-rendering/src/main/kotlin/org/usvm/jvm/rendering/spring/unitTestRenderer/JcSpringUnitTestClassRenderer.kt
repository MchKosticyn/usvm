package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.jvm.rendering.spring.JcSpringImportManager
import org.usvm.jvm.rendering.testRenderer.JcTestInfo
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestClassRenderer
import org.usvm.test.api.UTest

open class JcSpringUnitTestClassRenderer : JcUnsafeTestClassRenderer {
    override val importManager: JcSpringImportManager get() = super.importManager as JcSpringImportManager

    constructor(
        name: String,
        importManager: JcSpringImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath
    ) : super(name, importManager, identifiersManager, cp)

    constructor(
        decl: ClassOrInterfaceDeclaration,
        importManager: JcSpringImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath
    ) : super(decl, importManager, identifiersManager, cp)

    override fun createTestRenderer(
        test: UTest,
        testInfo: JcTestInfo,
        identifiersManager: JcIdentifiersManager,
        name: SimpleName,
        testAnnotation: AnnotationExpr,
    ): JcTestRenderer {
        return JcSpringUnitTestRenderer(
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
