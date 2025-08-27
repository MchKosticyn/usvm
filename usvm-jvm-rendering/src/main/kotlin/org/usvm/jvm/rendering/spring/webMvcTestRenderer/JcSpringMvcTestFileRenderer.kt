package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestFileRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager

class JcSpringMvcTestFileRenderer : JcSpringUnitTestFileRenderer {
    private constructor(
        controller: JcClassType,
        cu: CompilationUnit,
        importManager: JcUnsafeImportManager,
        cp: JcClasspath,
    ) : super(cu, importManager, cp) {
        this.controller = controller
    }

    private constructor(
        controller: JcClassType,
        packageName: String?,
        importManager: JcUnsafeImportManager,
        cp: JcClasspath,
    ) : super(packageName, importManager, cp) {
        this.controller = controller
    }

    constructor(
        controller: JcClassType,
        cu: CompilationUnit,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
    ) : this(
        controller,
        cu,
        JcUnsafeImportManager(cu, reflectionUtilsInlineStrategy),
        cp
    )

    constructor(
        controller: JcClassType,
        packageName: String?,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy
    ) : this(
        controller,
        packageName,
        JcUnsafeImportManager(null, reflectionUtilsInlineStrategy),
        cp
    )

    private val controller: JcClassType

    override fun classRendererFor(declaration: ClassOrInterfaceDeclaration): JcSpringMvcTestClassRenderer {
        return JcSpringMvcTestClassRenderer(controller, declaration, importManager, identifiersManager, cp)
    }

    override fun classRendererFor(name: String): JcSpringMvcTestClassRenderer {
        return JcSpringMvcTestClassRenderer(controller, name, importManager, identifiersManager, cp)
    }
}
