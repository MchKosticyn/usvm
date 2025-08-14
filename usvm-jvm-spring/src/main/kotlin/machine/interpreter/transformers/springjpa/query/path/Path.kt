package machine.interpreter.transformers.springjpa.query.path

import machine.interpreter.transformers.springjpa.query.CommonInfo
import org.usvm.spring.query.path.GeneralPath
import org.usvm.spring.query.path.Path
import org.usvm.spring.query.path.SimplePath

fun GeneralPath.isSimple() = path.isSimple()

fun GeneralPath.applyAliases(common: CommonInfo) = path.applyAliases(common)

// TODO: indexing (you cant indexing in path when in join target or similar)
fun GeneralPath.fullPath(): SimplePath {
    return path
}

fun Path.isSimple() = root.isSimple()

fun Path.applyAliases(common: CommonInfo): String {
    val rootName = root.applyAliases(common)
    return cont?.let { "${rootName}.${it.applyAliases(common)}" } ?: rootName
}

fun Path.getAlias(): Pair<String, String>? {
    if (alias == null) return null
    return alias!! to toString()
}

fun SimplePath.applyAliases(common: CommonInfo): String {
    fun apply(alias: String) = common.aliases.getOrDefault(alias, alias)
    return apply(root) + cont.joinToString(prefix = ".") { apply(it) }
}

fun SimplePath.flat() = root + cont.joinToString(prefix = ".")

fun SimplePath.isSimple() = cont.isEmpty()
