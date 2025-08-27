package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestFileRenderer

open class JcSpringUnitTestFileRenderer: JcUnsafeTestFileRenderer {

    protected val isAccessibleFromTestClass: (JcClassOrInterface) -> Boolean

    protected constructor(
        cu: CompilationUnit,
        importManager: JcUnsafeImportManager,
        cp: JcClasspath,
        accessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : super(cu, importManager, cp) {
        this.isAccessibleFromTestClass = accessibleFromTestClass
    }

    protected constructor(
        packageName: String?,
        importManager: JcUnsafeImportManager,
        cp: JcClasspath,
        accessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : super(packageName, importManager, cp) {
        this.isAccessibleFromTestClass = accessibleFromTestClass
    }

    constructor(
        cu: CompilationUnit,
        cp: JcClasspath,
        inlineUsvmUtils: Boolean = false,
        accessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : this(
        cu,
        JcUnsafeImportManager(cu, inlineUsvmUtils),
        cp,
        accessibleFromTestClass
    )

    constructor(
        packageName: String?,
        cp: JcClasspath,
        inlineUsvmUtils: Boolean = false,
        accessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : this(
        packageName,
        JcUnsafeImportManager(null, inlineUsvmUtils),
        cp,
        accessibleFromTestClass
    )

    override fun classRendererFor(declaration: ClassOrInterfaceDeclaration): JcSpringUnitTestClassRenderer {
        return JcSpringUnitTestClassRenderer(declaration, importManager, identifiersManager, cp, isAccessibleFromTestClass)
    }

    override fun classRendererFor(name: String): JcSpringUnitTestClassRenderer {
        return JcSpringUnitTestClassRenderer(name, importManager, identifiersManager, cp, isAccessibleFromTestClass)
    }
}
