package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestClassRenderer
import org.usvm.jvm.rendering.unsafeRenderer.ReflectionUtilNames
import org.usvm.test.api.UTest

open class JcSpringUnitTestClassRenderer : JcUnsafeTestClassRenderer {

    constructor(name: String): super(name, ReflectionUtilNames.SPRING.fullName)

    constructor(
        name: String,
        reflectionUtilsFullName: String
    ) : super(name, reflectionUtilsFullName)

    constructor(decl: ClassOrInterfaceDeclaration): this(decl, ReflectionUtilNames.SPRING.fullName)

    constructor(
        decl: ClassOrInterfaceDeclaration,
        reflectionUtilsFullName: String
    ) : super(decl, reflectionUtilsFullName)

    override fun createTestRenderer(
        test: UTest,
        identifiersManager: JcIdentifiersManager,
        name: SimpleName,
        testAnnotation: AnnotationExpr,
    ): JcTestRenderer {
        return JcSpringUnitTestRenderer(
            test,
            this,
            importManager,
            identifiersManager,
            name,
            testAnnotation
        )
    }
}
