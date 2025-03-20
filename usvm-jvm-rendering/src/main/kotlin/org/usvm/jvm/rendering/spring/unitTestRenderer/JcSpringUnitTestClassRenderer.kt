package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestClassRenderer
import org.usvm.jvm.rendering.unsafeRenderer.ReflectionUtilNames
import org.usvm.test.api.UTest

open class JcSpringUnitTestClassRenderer : JcUnsafeTestClassRenderer {

    constructor(name: String, cp: JcClasspath): super(name, ReflectionUtilNames.SPRING.fullName, cp)

    constructor(
        name: String,
        reflectionUtilsFullName: String,
        cp: JcClasspath
    ) : super(name, reflectionUtilsFullName, cp)

    constructor(decl: ClassOrInterfaceDeclaration, cp: JcClasspath): this(decl, ReflectionUtilNames.SPRING.fullName, cp)

    constructor(
        decl: ClassOrInterfaceDeclaration,
        reflectionUtilsFullName: String,
        cp: JcClasspath
    ) : super(decl, reflectionUtilsFullName, cp)

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
            cp,
            name,
            testAnnotation
        )
    }
}
