package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.SimpleName
import kotlin.math.max

class JcIdentifiersManager private constructor(
    private val prefixIndexer: MutableMap<String, Int>
) {
    companion object {
        private fun MutableMap<String, Int>.addAll(values: List<String>) {
            values.forEach { value ->
                val index = value.takeLastWhile { it.isDigit() }.toIntOrNull() ?: 0
                val prefix = value.dropLastWhile { it.isDigit() }
                compute(prefix) { pref, curMax -> max(curMax ?: 0, index) }
            }
        }
    }

    constructor(manager: JcIdentifiersManager) : this(manager.prefixIndexer.toMutableMap())

    constructor(cu: CompilationUnit? = null): this(mutableMapOf<String, Int>()) {
        val classes = cu?.types?.map { it.name.asString() }.orEmpty()
        prefixIndexer.addAll(classes)
    }

    fun extendedWith(declaration: ClassOrInterfaceDeclaration): JcIdentifiersManager {
        val newManager = JcIdentifiersManager(this)

        val fields = declaration.fields.flatMap { field -> field.variables.map { v -> v.name.asString() } }
        newManager.prefixIndexer.addAll(fields)

        val methods = declaration.methods.map { it.name.asString() }
        newManager.prefixIndexer.addAll(methods)

        return newManager
    }

    fun generateIdentifier(prefix: String): SimpleName {
        val normalizedPrefix = prefix.normalized()
        val id = prefixIndexer.merge(normalizedPrefix, 0) { a, _ -> a + 1 }
        val suffix = if (id == 0) "" else id.toString()
        return SimpleName("$normalizedPrefix$suffix")
    }

    operator fun get(prefix: String): SimpleName = generateIdentifier(prefix)

    private fun String.normalized(): String =
        this.replace("<", "").replace(">", "").replace("$", "")
}
