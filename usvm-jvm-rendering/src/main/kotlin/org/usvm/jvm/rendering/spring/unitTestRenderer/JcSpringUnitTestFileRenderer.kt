package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestFileRenderer

open class JcSpringUnitTestFileRenderer: JcUnsafeTestFileRenderer {

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
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
    ) : this(
        cu,
        JcUnsafeImportManager(cu, reflectionUtilsInlineStrategy),
        cp
    )

    constructor(
        packageName: String?,
        cp: JcClasspath,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy,
    ) : this(
        packageName,
        JcUnsafeImportManager(null, reflectionUtilsInlineStrategy),
        cp
    )

    override fun classRendererFor(declaration: ClassOrInterfaceDeclaration): JcSpringUnitTestClassRenderer {
        return JcSpringUnitTestClassRenderer(declaration, importManager, identifiersManager, cp)
    }

    override fun classRendererFor(name: String): JcSpringUnitTestClassRenderer {
        return JcSpringUnitTestClassRenderer(name, importManager, identifiersManager, cp)
    }
}
