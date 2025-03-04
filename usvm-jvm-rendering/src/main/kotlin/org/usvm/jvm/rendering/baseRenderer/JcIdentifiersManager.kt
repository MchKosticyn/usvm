package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.ast.expr.SimpleName

class JcIdentifiersManager private constructor(
    private val prefixIndexer: MutableMap<String, Int>
) {
    constructor(manager: JcIdentifiersManager) : this(manager.prefixIndexer.toMutableMap())

    constructor(): this(mutableMapOf<String, Int>())

    fun generateIdentifier(prefix: String): SimpleName {
        val id = prefixIndexer.merge(prefix, 0) { a, _ -> a + 1 }
        val suffix = if (id == 0) "" else id.toString()
        return SimpleName("$prefix$suffix")
    }

    operator fun get(prefix: String): SimpleName = generateIdentifier(prefix)
}
