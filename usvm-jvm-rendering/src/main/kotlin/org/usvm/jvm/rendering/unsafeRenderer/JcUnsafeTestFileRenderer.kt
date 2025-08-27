package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.testRenderer.JcTestFileRenderer

open class JcUnsafeTestFileRenderer : JcTestFileRenderer {
    protected constructor(
        cu: CompilationUnit,
        importManager: JcUnsafeImportManager,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy = ReflectionUtilsInlineStrategy.NoInline
    ) : super(cu, importManager, cp) {
        this.reflectionUtilsInlineStrategy = reflectionUtilsInlineStrategy
    }

    protected constructor(
        packageName: String?,
        importManager: JcUnsafeImportManager,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy = ReflectionUtilsInlineStrategy.NoInline
    ) : super(packageName, importManager, cp) {
        this.reflectionUtilsInlineStrategy = reflectionUtilsInlineStrategy
    }

    constructor(
        cu: CompilationUnit,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy
    ) : this(
        cu,
        JcUnsafeImportManager(cu, reflectionUtilsInlineStrategy),
        cp,
        reflectionUtilsInlineStrategy
    )

    constructor(
        packageName: String?,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy
    ) : this(
        packageName,
        JcUnsafeImportManager(null, reflectionUtilsInlineStrategy),
        cp,
        reflectionUtilsInlineStrategy
    )

    override val importManager: JcUnsafeImportManager
        get() = super.importManager as JcUnsafeImportManager

    private val reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy

    override fun classRendererFor(declaration: ClassOrInterfaceDeclaration): JcUnsafeTestClassRenderer {
        return JcUnsafeTestClassRenderer(declaration, importManager, identifiersManager, cp)
    }

    override fun classRendererFor(name: String): JcUnsafeTestClassRenderer =
        JcUnsafeTestClassRenderer(name, importManager, identifiersManager, cp)


    override fun renderInternal(): CompilationUnit {
        var cu = super.renderInternal()
        cu = reflectionUtilsInlineStrategy.addReflectionUtils(importManager, cu)
        return cu
    }
}
