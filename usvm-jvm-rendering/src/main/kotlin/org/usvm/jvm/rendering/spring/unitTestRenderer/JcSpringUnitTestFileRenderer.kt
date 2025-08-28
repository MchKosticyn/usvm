package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.spring.JcSpringReflectionUtilsRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestFileRenderer

open class JcSpringUnitTestFileRenderer: JcUnsafeTestFileRenderer {

    override val unsafeUtilsRenderer: JcSpringReflectionUtilsRenderer
        get() = _unsafeUtilsRenderer

    private val _unsafeUtilsRenderer: JcSpringReflectionUtilsRenderer

    protected constructor(
        cu: CompilationUnit,
        importManager: JcImportManager,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
    ) : super(cu, importManager, cp, reflectionUtilsInlineStrategy) {
        this._unsafeUtilsRenderer = JcSpringReflectionUtilsRenderer(reflectionUtilsInlineStrategy, this)
    }

    protected constructor(
        packageName: String?,
        importManager: JcImportManager,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
    ) : super(packageName, importManager, cp, reflectionUtilsInlineStrategy) {
        this._unsafeUtilsRenderer = JcSpringReflectionUtilsRenderer(reflectionUtilsInlineStrategy, this)
    }

    constructor(
        cu: CompilationUnit,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
    ) : this(
        cu,
        JcImportManager(cu),
        cp,
        reflectionUtilsInlineStrategy
    )

    constructor(
        packageName: String?,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
    ) : this(
        packageName,
        JcImportManager(null),
        cp,
        reflectionUtilsInlineStrategy
    )

    override fun classRendererFor(declaration: ClassOrInterfaceDeclaration): JcSpringUnitTestClassRenderer {
        return JcSpringUnitTestClassRenderer(declaration, importManager, identifiersManager, cp, unsafeUtilsRenderer)
    }

    override fun classRendererFor(name: String): JcSpringUnitTestClassRenderer {
        return JcSpringUnitTestClassRenderer(name, importManager, identifiersManager, cp, unsafeUtilsRenderer)
    }
}
