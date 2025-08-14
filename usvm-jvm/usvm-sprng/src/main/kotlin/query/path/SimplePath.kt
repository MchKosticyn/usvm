package machine.interpreter.transformers.springjpa.query.path

import machine.interpreter.transformers.springjpa.query.CommonInfo

class SimplePath(
    val root: String, // not case-sensitive
    val cont: List<String> // case-sensitive
) {

    fun isSimple() = cont.isEmpty()

    fun applyAliases(common: CommonInfo): String {
        fun apply(alias: String) = common.aliases.getOrDefault(alias, alias)
        return apply(root) + cont.joinToString(prefix = ".") { apply(it) }
    }

    fun flat() = root + cont.joinToString(prefix = ".")
}
