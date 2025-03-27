package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.NodeList

open class JcImportManager(cu: CompilationUnit? = null) {
    protected val names: MutableSet<String>
    protected val staticNames: MutableSet<String>
    protected val packages: MutableSet<String>
    protected val staticPackages: MutableSet<String>

    private val simpleToPackage: MutableMap<String, String>

    init {
        if (cu != null) {
            names = cu.imports.filter { !it.isAsterisk && !it.isStatic }.map { it.nameAsString }.toMutableSet()
            staticNames = cu.imports.filter { !it.isAsterisk && it.isStatic }.map { it.nameAsString }.toMutableSet()
            packages = cu.imports.filter { it.isAsterisk && !it.isStatic }.map { it.nameAsString }.toMutableSet()
            staticPackages = cu.imports.filter { it.isAsterisk && it.isStatic }.map { it.nameAsString }.toMutableSet()

            simpleToPackage = (names + staticNames).associateBy { it.split(".").last() }.toMutableMap()
        } else {
            names = mutableSetOf()
            simpleToPackage = mutableMapOf()
            staticNames = mutableSetOf()
            packages = mutableSetOf()
            staticPackages = mutableSetOf()
        }

        packages.add("java.lang")
    }

    protected open fun add(
        packageName: String,
        simpleName: String,
        packages: MutableSet<String>,
        names: MutableSet<String>
    ): Boolean {
        if (packageName in packages) return true
        val fullName = "$packageName.$simpleName"
        if (fullName in names) return true
        if (simpleToPackage.putIfAbsent(simpleName, fullName) != null)
            return false

        names.add(fullName)

        return true
    }

    fun add(import: String): Boolean {
        val tokens = import.split(".")
        return add(tokens.dropLast(1).joinToString("."), tokens.last())
    }

    fun add(packageName: String, simpleName: String): Boolean {
        return add(packageName, simpleName, packages, names)
    }

    fun addStatic(import: String): Boolean {
        val tokens = import.split(".")
        return addStatic(tokens.dropLast(1).joinToString("."), tokens.last())
    }

    fun addStatic(packageName: String, simpleName: String): Boolean {
        return add(packageName, simpleName, staticPackages, staticNames)
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
