package org.usvm.jvm.rendering.unsafeRenderer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import kotlin.jvm.optionals.getOrNull
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.testRenderer.JcTestFileRenderer

open class JcUnsafeTestFileRenderer : JcTestFileRenderer {
    protected constructor(
        cu: CompilationUnit,
        importManager: JcUnsafeImportManager,
        cp: JcClasspath,
        inlineUsvmUtils: Boolean = false
    ) : super(cu, importManager, cp) {
        this.shouldInlineUsvmUtils = inlineUsvmUtils
    }

    protected constructor(
        packageName: String,
        importManager: JcUnsafeImportManager,
        cp: JcClasspath,
        inlineUsvmUtils: Boolean = false
    ) : super(packageName, importManager, cp) {
        this.shouldInlineUsvmUtils = inlineUsvmUtils
    }

    constructor(
        cu: CompilationUnit,
        cp: JcClasspath,
        inlineUsvmUtils: Boolean
    ) : this(cu, JcUnsafeImportManager(cu, inlineUsvmUtils), cp, inlineUsvmUtils)

    constructor(
        packageName: String,
        cp: JcClasspath,
        inlineUsvmUtils: Boolean
    ) : this(packageName, JcUnsafeImportManager(null, inlineUsvmUtils), cp, inlineUsvmUtils)

    override val importManager: JcUnsafeImportManager
        get() = super.importManager as JcUnsafeImportManager

    private val shouldInlineUsvmUtils: Boolean

    override fun classRendererFor(declaration: ClassOrInterfaceDeclaration): JcUnsafeTestClassRenderer {
        return JcUnsafeTestClassRenderer(declaration, importManager, identifiersManager, cp)
    }

    override fun classRendererFor(name: String): JcUnsafeTestClassRenderer =
        JcUnsafeTestClassRenderer(name, importManager, identifiersManager, cp)

    private fun addUsvmReflectionUtilClassWith(methods: Set<String>, cu: CompilationUnit): CompilationUnit {
        if (methods.isEmpty()) return cu

        val filteredUtilCu =
            this::class.java.classLoader.getResourceAsStream("ReflectionUtils.java").use { stream ->
                val usvmUtils = StaticJavaParser.parse(stream)
                val utilsClass = usvmUtils.getClassByName("ReflectionUtils").get()
                utilsClass.members.removeIf { it.isMethodDeclaration && (it.asMethodDeclaration().name.asString() !in methods) }
                usvmUtils
            }

        var previousUtils = cu.getClassByName("ReflectionUtils").getOrNull()
        val requiredUtils = filteredUtilCu.getClassByName("ReflectionUtils").get()

        if (previousUtils != null) {
            mergeUtilClass(previousUtils, requiredUtils)
        } else {
            previousUtils = requiredUtils
            cu.addType(previousUtils)
        }

        filteredUtilCu.imports.forEach { importDecl -> cu.addImport(importDecl) }
        previousUtils.modifiers = NodeList()
        previousUtils.allContainedComments.forEach { it.remove() }

        return cu
    }

    private fun mergeUtilClass(prev: ClassOrInterfaceDeclaration, extra: ClassOrInterfaceDeclaration) {
        val declaredMembersNames = prev.members.mapNotNull { if (it.isMethodDeclaration) it.asMethodDeclaration().name else null }

        extra.members.filter { it.isMethodDeclaration }.forEach { declaration ->
            if (declaration.asMethodDeclaration().name !in declaredMembersNames) {
                prev.addMember(declaration)
            }
        }
    }

    override fun renderInternal(): CompilationUnit {
        var cu = super.renderInternal()

        if (shouldInlineUsvmUtils) {
            val requiredUsvmMethods = importManager.extractUsedUsvmUtilMethods()
            cu = addUsvmReflectionUtilClassWith(requiredUsvmMethods, cu)
        }
        return cu
    }
}
