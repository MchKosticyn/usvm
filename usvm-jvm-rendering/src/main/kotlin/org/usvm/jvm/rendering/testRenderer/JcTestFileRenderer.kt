package org.usvm.jvm.rendering.testRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.SimpleName
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcFileRenderer
import org.usvm.jvm.rendering.baseRenderer.JcImportManager

open class JcTestFileRenderer : JcFileRenderer {
    protected constructor(cu: CompilationUnit, importManager: JcImportManager, cp: JcClasspath) : super(cu, importManager, cp)

    protected constructor(packageName: String, importManager: JcImportManager, cp: JcClasspath) : super(packageName, importManager, cp)

    constructor(cu: CompilationUnit, cp: JcClasspath) : this(cu, JcImportManager(cu), cp)

    constructor(packageName: String, cp: JcClasspath) : this(packageName, JcImportManager(), cp)

    override fun classRendererFor(declaration: ClassOrInterfaceDeclaration): JcTestClassRenderer {
        return JcTestClassRenderer(declaration, importManager, identifiersManager, cp)
    }

    protected open fun classRendererFor(name: String): JcTestClassRenderer =
        JcTestClassRenderer(
            name,
            importManager,
            identifiersManager,
            cp
        )

    fun getOrAddClass(testClassName: String): JcTestClassRenderer {
        val existing = findRenderingClass(SimpleName(testClassName))
        if (existing != null) return existing as JcTestClassRenderer

        val renderer = classRendererFor(testClassName)

        addRenderingClass(renderer)
        return renderer
    }
}
