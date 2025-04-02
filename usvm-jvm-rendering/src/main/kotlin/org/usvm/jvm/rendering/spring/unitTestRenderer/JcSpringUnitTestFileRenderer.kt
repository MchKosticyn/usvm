package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.spring.JcSpringImportManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestFileRenderer

open class JcSpringUnitTestFileRenderer: JcUnsafeTestFileRenderer {
    override val importManager: JcSpringImportManager
        get() = super.importManager as JcSpringImportManager
    protected constructor(cu: CompilationUnit, importManager: JcSpringImportManager, cp: JcClasspath) : super(cu, importManager, cp)

    protected constructor(packageName: String, importManager: JcSpringImportManager, cp: JcClasspath) : super(packageName, importManager, cp)

    constructor(cu: CompilationUnit, cp: JcClasspath) : this(cu, JcSpringImportManager(cu), cp)

    constructor(packageName: String, cp: JcClasspath) : this(packageName, JcSpringImportManager(), cp)


    override fun classRendererFor(declaration: ClassOrInterfaceDeclaration): JcSpringUnitTestClassRenderer {
        return JcSpringUnitTestClassRenderer(declaration, importManager, identifiersManager, cp)
    }

    override fun classRendererFor(name: String): JcSpringUnitTestClassRenderer {
        return JcSpringUnitTestClassRenderer(name, importManager, identifiersManager, cp)
    }
}