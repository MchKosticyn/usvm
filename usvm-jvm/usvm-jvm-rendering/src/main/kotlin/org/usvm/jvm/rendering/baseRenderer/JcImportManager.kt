package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.NodeList

class JcImportManager(cu: CompilationUnit? = null) {
    private val names: MutableSet<String>
    private val staticNames: MutableSet<String>
    private val packages: MutableSet<String>
    private val staticPackages: MutableSet<String>

    private val simpleToPackage: MutableMap<String, String>

    init {
        if (cu != null) {
            names = cu.imports.filter { declaration -> !declaration.isAsterisk && !declaration.isStatic }.map { decl -> decl.nameAsString }.toMutableSet()
            staticNames = cu.imports.filter { declaration -> !declaration.isAsterisk && declaration.isStatic }.map { decl -> decl.nameAsString }.toMutableSet()
            packages = cu.imports.filter { declaration -> declaration.isAsterisk && !declaration.isStatic }.map { decl -> decl.nameAsString }.toMutableSet()
            staticPackages = cu.imports.filter { declaration -> declaration.isAsterisk && declaration.isStatic }.map { decl -> decl.nameAsString }.toMutableSet()

            simpleToPackage = (names + staticNames).associateBy { it.split(".").last() }.toMutableMap()
        } else {
            names = mutableSetOf()
            simpleToPackage = mutableMapOf()
            staticNames = mutableSetOf()
            packages = mutableSetOf()
            staticPackages = mutableSetOf()
        }
    }

    fun add(import: String): Boolean {
        val tokens = import.split(".")
        return add(tokens.dropLast(1).joinToString("."), tokens.last())
    }

    fun add(packageName: String, simpleName: String): Boolean {
        if (packageName in packages) return true
        val fullName = "$packageName.$simpleName"
        if (fullName in names) return true
        if (simpleToPackage.putIfAbsent(simpleName, fullName) != null) return false
        names.add(fullName)
        return true
    }

    fun addStatic(import: String): Boolean {
        val tokens = import.split(".")
        return addStatic(tokens.dropLast(1).joinToString("."), tokens.last())
    }

    fun addStatic(packageName: String, simpleName: String): Boolean {
        if (packageName in staticPackages) return true
        val fullName = "$packageName.$simpleName"
        if (fullName in staticNames) return true
        if (simpleToPackage.putIfAbsent(simpleName, fullName) != null) return false
        staticNames.add(fullName)
        return true
    }

    fun render(): NodeList<ImportDeclaration> {
        val declarations = buildList {
            addAll(names.map { ImportDeclaration(it, false, false) })
            addAll(packages.map { ImportDeclaration(it, false, true) })
            addAll(staticNames.map { ImportDeclaration(it, true, false) })
            addAll(staticPackages.map { ImportDeclaration(it, true, true) })
        }
        return NodeList(declarations)
    }
}
