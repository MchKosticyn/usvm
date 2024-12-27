package org.usvm.machine.interpreter.transformers.springjpa.query.path

import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo

class SimplePathCtx(
    val root: String, // not case-sensitive
    val cont: List<String> // case-sensitive
) {

    fun isSimple(): Boolean {
        return cont.isEmpty()
    }

    fun applyAliases(common: CommonInfo): String {
        fun apply(alias: String): String {
            return common.aliases.getOrDefault(alias, alias)
        }
        return apply(root) + cont.joinToString(prefix = ".") { apply(it) }
    }

    fun flat(): String {
        return root + cont.joinToString(prefix = ".")
    }
}
