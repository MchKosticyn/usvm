package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.testRenderer.JcTestFileRenderer

open class JcUnsafeTestFileRenderer : JcTestFileRenderer {

    protected open val unsafeUtilsRenderer: JcUnsafeUtilsRenderer get()  = _unsafeUtilsRenderer

    private val _unsafeUtilsRenderer: JcUnsafeUtilsRenderer

    protected constructor(
        cu: CompilationUnit,
        importManager: JcImportManager,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy
    ) : super(cu, importManager, cp) {
        this._unsafeUtilsRenderer = JcUnsafeUtilsRenderer(reflectionUtilsInlineStrategy, this)
    }

    protected constructor(
        packageName: String?,
        importManager: JcImportManager,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy
    ) : super(packageName, importManager, cp) {
        this._unsafeUtilsRenderer = JcUnsafeUtilsRenderer(reflectionUtilsInlineStrategy, this)
    }

    constructor(
        cu: CompilationUnit,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy
    ) : this(
        cu,
        JcImportManager(cu),
        cp,
        reflectionUtilsInlineStrategy
    )

    constructor(
        packageName: String?,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy
    ) : this(
        packageName,
        JcImportManager(null),
        cp,
        reflectionUtilsInlineStrategy
    )

    override fun classRendererFor(declaration: ClassOrInterfaceDeclaration): JcUnsafeTestClassRenderer {
        return JcUnsafeTestClassRenderer(declaration, importManager, identifiersManager, cp, unsafeUtilsRenderer)
    }

    override fun classRendererFor(name: String): JcUnsafeTestClassRenderer =
        JcUnsafeTestClassRenderer(name, importManager, identifiersManager, cp, unsafeUtilsRenderer)


    override fun renderInternal(): CompilationUnit {
        return unsafeUtilsRenderer.addReflectionUtils(importManager, super.renderInternal())
    }
}
