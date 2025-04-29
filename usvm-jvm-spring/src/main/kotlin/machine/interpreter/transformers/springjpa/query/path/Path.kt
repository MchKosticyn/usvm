package machine.interpreter.transformers.springjpa.query.path

import machine.interpreter.transformers.springjpa.query.CommonInfo

class Path(
    val root: GeneralPath,
    val cont: SimplePath?,
    var alias: String?
) {

    fun isSimple() = root.isSimple()

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
