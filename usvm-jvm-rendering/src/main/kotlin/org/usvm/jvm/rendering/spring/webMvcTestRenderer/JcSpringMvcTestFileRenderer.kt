package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestFileRenderer
import org.usvm.jvm.rendering.spring.JcSpringImportManager

class JcSpringMvcTestFileRenderer : JcSpringUnitTestFileRenderer {
    private constructor(
        controller: JcClassType,
        cu: CompilationUnit,
        importManager: JcSpringImportManager,
        cp: JcClasspath
    ) : super(cu, importManager, cp) {
        this.controller = controller
    }

    private constructor(
        controller: JcClassType,
        packageName: String,
        importManager: JcSpringImportManager,
        cp: JcClasspath
    ) : super(packageName, importManager, cp) {
        this.controller = controller
    }

    constructor(
        controller: JcClassType,
        cu: CompilationUnit,
        cp: JcClasspath
    ) : this(
        controller,
        cu,
        JcSpringImportManager(cu),
        cp
    )

    constructor(
        controller: JcClassType,
        packageName: String,
        cp: JcClasspath
    ) : this(
        controller,
        packageName,
        JcSpringImportManager(),
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