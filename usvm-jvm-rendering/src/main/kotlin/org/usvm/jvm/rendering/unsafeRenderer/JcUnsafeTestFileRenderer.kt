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
    ) : super(cu, importManager, cp)

    protected constructor(
        packageName: String?,
        importManager: JcUnsafeImportManager,
        cp: JcClasspath,
    ) : super(packageName, importManager, cp)

    constructor(
        cu: CompilationUnit,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy
    ) : this(
        cu,
        JcUnsafeImportManager(cu, reflectionUtilsInlineStrategy),
        cp
    )

    constructor(
        packageName: String?,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy
    ) : this(
        packageName,
        JcUnsafeImportManager(null, reflectionUtilsInlineStrategy),
        cp
    )

    override val importManager: JcUnsafeImportManager
        get() = super.importManager as JcUnsafeImportManager

    override fun classRendererFor(declaration: ClassOrInterfaceDeclaration): JcUnsafeTestClassRenderer {
        return JcUnsafeTestClassRenderer(declaration, importManager, identifiersManager, cp)
    }

    override fun classRendererFor(name: String): JcUnsafeTestClassRenderer =
        JcUnsafeTestClassRenderer(name, importManager, identifiersManager, cp)


    override fun renderInternal(): CompilationUnit {
        return importManager.reflectionUtilsInlineStrategy.addReflectionUtils(importManager, super.renderInternal())
    }
}
