package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.spring.JcSpringReflectionUtilsRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestFileRenderer

open class JcSpringUnitTestFileRenderer: JcUnsafeTestFileRenderer {

    private val isAccessibleFromTestClass: (JcClassOrInterface) -> Boolean

    override val unsafeUtilsRenderer: JcSpringReflectionUtilsRenderer by lazy {
        JcSpringReflectionUtilsRenderer(importManager, reflectionUtilsInlineStrategy, isAccessibleFromTestClass)
    }

    protected constructor(
        cu: CompilationUnit,
        importManager: JcImportManager,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
        isAccessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : super(cu, importManager, cp, reflectionUtilsInlineStrategy) {
        this.isAccessibleFromTestClass = isAccessibleFromTestClass
    }

    protected constructor(
        packageName: String?,
        importManager: JcImportManager,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
        isAccessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : super(packageName, importManager, cp, reflectionUtilsInlineStrategy) {
        this.isAccessibleFromTestClass = isAccessibleFromTestClass
    }

    constructor(
        cu: CompilationUnit,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
        isAccessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : this(
        cu,
        JcImportManager(cu),
        cp,
        reflectionUtilsInlineStrategy,
        isAccessibleFromTestClass
    )

    constructor(
        packageName: String?,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
        isAccessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : this(
        packageName,
        JcImportManager(null),
        cp,
        reflectionUtilsInlineStrategy,
        isAccessibleFromTestClass
    )

    override fun classRendererFor(declaration: ClassOrInterfaceDeclaration): JcSpringUnitTestClassRenderer {
        return JcSpringUnitTestClassRenderer(declaration, importManager, identifiersManager, cp, unsafeUtilsRenderer)
    }

    override fun classRendererFor(name: String): JcSpringUnitTestClassRenderer {
        return JcSpringUnitTestClassRenderer(name, importManager, identifiersManager, cp, unsafeUtilsRenderer)
    }
}
