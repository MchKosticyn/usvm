package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.ast.expr.SimpleName

class JcIdentifiersManager(prefixMaxIndices: Map<String, Int>) {
    private val prefixIndexer: MutableMap<String, Int> = prefixMaxIndices.toMutableMap()

    constructor(manager: JcIdentifiersManager) : this(manager.prefixIndexer)

    constructor(): this(mutableMapOf<String, Int>())

    fun generateIdentifier(prefix: String): SimpleName {
        return SimpleName("$prefix${prefixIndexer.merge(prefix, 0) { a, _ -> a + 1 }}")
    }

    operator fun get(prefix: String): SimpleName = generateIdentifier(prefix)
}
