package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.ImportDeclaration
import com.github.javaparser.ast.NodeList

class JcImportManager(cu: CompilationUnit? = null) {
    private val importedFullNames: MutableSet<String>
    private val importedPackages: MutableSet<String>

    init {
        if (cu != null) {
            val (asterisk, nonAsterisk) = cu.imports.partition { import -> import.isAsterisk }
            importedPackages = asterisk.map { decl -> decl.nameAsString }.toMutableSet()
            importedFullNames = nonAsterisk.map { decl -> decl.nameAsString }.toMutableSet()
        } else {
            importedPackages = mutableSetOf()
            importedFullNames = mutableSetOf()
        }
    }

    fun add(import: String) {

    }

    fun render(): NodeList<ImportDeclaration> {
        TODO()
    }
}
