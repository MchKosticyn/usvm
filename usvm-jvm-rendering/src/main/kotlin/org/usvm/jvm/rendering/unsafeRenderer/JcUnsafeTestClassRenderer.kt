package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import kotlin.jvm.optionals.getOrNull
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
        reflectionUtilsFullName: String,
        cp: JcClasspath
    ) : super(JcUnsafeImportManager(reflectionUtilsFullName), name, cp)

    constructor(decl: ClassOrInterfaceDeclaration, cp: JcClasspath): this(decl, ReflectionUtilNames.USVM.fullName, cp)

    constructor(
        decl: ClassOrInterfaceDeclaration,
        reflectionUtilsFullName: String,
        cp: JcClasspath
    ) : super(JcUnsafeImportManager(reflectionUtilsFullName, decl.findCompilationUnit().getOrNull()), decl, cp)

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
            identifiersManager,
            cp,
            name,
            testAnnotation
        )
    }
}
