package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.TypeDeclaration
import com.github.javaparser.ast.expr.SimpleName
import kotlin.jvm.optionals.getOrNull
import org.jacodb.api.jvm.JcClasspath

open class JcFileRenderer : JcCodeRenderer<CompilationUnit> {

    companion object {
        private fun resolvePackageDeclarationFrom(packageName: String?, cu: CompilationUnit?): PackageDeclaration {
            val existingPackageDecl = cu?.packageDeclaration?.getOrNull()
            val freshPackageDecl = packageName?.let { name ->
                val parsedName = StaticJavaParser.parseName(name)
                PackageDeclaration(parsedName)
            }

            return existingPackageDecl ?: freshPackageDecl ?: PackageDeclaration(StaticJavaParser.parseName(defaultRenderedPackageName))
        }

        const val defaultRenderedPackageName = "org.usvm.generated"
    }

    private constructor(
        packageName: String?,
        cu: CompilationUnit?,
        importManager: JcImportManager,
        cp: JcClasspath
    ) : super(
        importManager,
        JcIdentifiersManager(cu),
        cp
    ) {
        this.packageDeclaration = resolvePackageDeclarationFrom(packageName, cu)
        this.existingMembers.addAll(cu?.types?.filterIsInstance<ClassOrInterfaceDeclaration>().orEmpty())
    }

    protected constructor(
        cu: CompilationUnit,
        importManager: JcImportManager,
        cp: JcClasspath
    ) : this(
        null,
        cu,
        importManager,
        cp
    )

    protected constructor(
        packageName: String,
        importManager: JcImportManager,
        cp: JcClasspath
    ) : this(
        packageName,
        null,
        importManager,
        cp
    )

    protected constructor(cu: CompilationUnit, cp: JcClasspath) : this(cu, JcImportManager(cu), cp)

    protected constructor(packageName: String, cp: JcClasspath): this(packageName, JcImportManager(), cp)

    protected val packageDeclaration: PackageDeclaration

    private val existingMembers: MutableList<ClassOrInterfaceDeclaration> = mutableListOf()
    private val renderingClasses: MutableList<JcClassRenderer> = mutableListOf()

    override fun renderInternal(): CompilationUnit {
        val renderedClasses = mutableListOf<TypeDeclaration<*>>()

        for (renderer in renderingClasses) {
            try {
                val classRender = renderer.render()
                renderedClasses.add(classRender)
            } catch (e: Throwable) {
                println("Renderer failed to render class: ${e.message}")
            }
        }

        val importDeclarations = importManager.render()

        val classEntries = NodeList(renderedClasses)
        classEntries.addAll(existingMembers)

        return CompilationUnit(
            packageDeclaration,
            importDeclarations,
            classEntries,
            null
        )
    }

    protected open fun classRendererFor(declaration: ClassOrInterfaceDeclaration): JcClassRenderer {
        return JcClassRenderer(declaration, importManager, identifiersManager, cp)
    }

    protected fun findRenderingClass(name: SimpleName): JcClassRenderer? {
        val existingRenderer = renderingClasses.find { it.name == name }
        if (existingRenderer != null) return existingRenderer

        val existingDecl = existingMembers.find { decl -> decl.name == name }

        if (existingDecl == null) return null

        existingMembers.removeIf { decl -> decl == existingDecl }
        val renderer= classRendererFor(existingDecl)
        addRenderingClass(renderer)

        return renderer
    }

    fun addRenderingClass(render: JcClassRenderer) {
        renderingClasses.add(render)
    }
}