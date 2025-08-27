package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestClassRenderer
import org.usvm.test.api.UTest

open class JcSpringUnitTestClassRenderer : JcUnsafeTestClassRenderer {
    override val importManager: JcUnsafeImportManager get() = super.importManager

    protected val isAccessibleFromTestClass: (JcClassOrInterface) -> Boolean

    constructor(
        name: String,
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        accessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : super(name, importManager, identifiersManager, cp) {
        this.isAccessibleFromTestClass = accessibleFromTestClass
    }

    constructor(
        decl: ClassOrInterfaceDeclaration,
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        accessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : super(decl, importManager, identifiersManager, cp) {
        this.isAccessibleFromTestClass = accessibleFromTestClass
    }

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
            isAccessibleFromTestClass
        )
    }
}
