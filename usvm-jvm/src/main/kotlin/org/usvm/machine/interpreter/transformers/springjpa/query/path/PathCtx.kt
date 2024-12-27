package org.usvm.machine.interpreter.transformers.springjpa.query.path

import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo

class PathCtx(
    val root: GeneralPathCtx,
    val cont: SimplePathCtx?,
    var alias: String?
) {

    fun isSimple(): Boolean {
        return root.isSimple()
    }

    override fun toString(): String {
        val rootName = root.toString()
        return cont?.let { "${rootName}.$it" } ?: rootName
    }

    fun applyAliases(common: CommonInfo): String {
        val rootName = root.applyAliases(common)
        return cont?.let { "${rootName}.${it.applyAliases(common)}" } ?: rootName
    }

    fun getAlias(): Pair<String, String>? {
        if (alias == null) return null
        return alias!! to toString()
    }

}
