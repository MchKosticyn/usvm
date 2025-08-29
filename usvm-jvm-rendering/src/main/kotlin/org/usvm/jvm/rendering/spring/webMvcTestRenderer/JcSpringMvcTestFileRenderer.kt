package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestFileRenderer

class JcSpringMvcTestFileRenderer : JcSpringUnitTestFileRenderer {
    private constructor(
        controller: JcClassType,
        cu: CompilationUnit,
        importManager: JcImportManager,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
        isAccessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : super(cu, importManager, cp, reflectionUtilsInlineStrategy, isAccessibleFromTestClass) {
        this.controller = controller
    }

    private constructor(
        controller: JcClassType,
        packageName: String?,
        importManager: JcImportManager,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
        isAccessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : super(packageName, importManager, cp, reflectionUtilsInlineStrategy, isAccessibleFromTestClass) {
        this.controller = controller
    }

    constructor(
        controller: JcClassType,
        cu: CompilationUnit,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
        isAccessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : this(
        controller,
        cu,
        JcImportManager(cu),
        cp,
        reflectionUtilsInlineStrategy,
        isAccessibleFromTestClass
    )

    constructor(
        controller: JcClassType,
        packageName: String?,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
        isAccessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : this(
        controller,
        packageName,
        JcImportManager(null),
        cp,
        reflectionUtilsInlineStrategy,
        isAccessibleFromTestClass
    )

    private val controller: JcClassType

    override fun classRendererFor(declaration: ClassOrInterfaceDeclaration): JcSpringMvcTestClassRenderer {
        return JcSpringMvcTestClassRenderer(controller, declaration, importManager, identifiersManager, cp, unsafeUtilsRenderer)
    }

    override fun classRendererFor(name: String): JcSpringMvcTestClassRenderer {
        return JcSpringMvcTestClassRenderer(controller, name, importManager, identifiersManager, cp, unsafeUtilsRenderer)
    }
}
