package machine.interpreter.transformers.springjpa.query.visitors

import jpa.JcTableInfoCollector
import org.jacodb.api.jvm.JcClasspath

private class TranslatorContext(
    val cp: JcClasspath,
    val collector: JcTableInfoCollector
) {

    // vets <=> Vet
    private val aliases: MutableMap<String, String> = HashMap()

    fun addAlias(alias: String, className: String) { aliases[alias] = className }
    fun getClassName(alias: String) = aliases[alias]!!

    fun searchAlias(fieldName: String) =
            aliases.entries.first { (alias, _) ->
                val tbl = collector.getTable(alias)!!
                    tbl.columnsInOrder().any { it.name == fieldName.lowercase() }
            }.key

    fun addPrefix(fieldName: String) = "${searchAlias(fieldName)}.$fieldName"

    fun addClassName(alias: String) = "${getClassName(alias)} $alias"

    fun getSomeAlias() = aliases.keys.first()

    fun isTable(alias: String): Boolean {
        if (aliases.contains(alias)) return true

        val className = collector.tables().singleOrNull { it.name == alias }?.origClassName?.split(".")?.last()
        if (className != null) {
            addAlias(alias, className)
            return true
        }

        return false
    }
}

class JPANativeQueryTranslator(
    val cp: JcClasspath,
    val query: String,
    val collector: JcTableInfoCollector
) {

    private val operatorsAndSymbols = listOf(
        ")", "(", ",", "=", "!", "!=", "<", ">", "<=", ">=", "+", "-", "*", "/"
    )

    fun isOperator(w: String) = operatorsAndSymbols.contains(w)

    private val keywords = listOf(
        "SELECT", "FROM", "WHERE", "INSERT", "INTO", "VALUES", "UPDATE", "SET", "DELETE", "DISTINCT",
        "AND", "OR", "NOT", "IN", "BETWEEN", "LIKE", "ILIKE", "IS", "NULL", "NOT", "EXISTS",
        "COUNT", "DATE", "SUM", "AVG", "MIN", "MAX", "GROUP", "BY", "HAVING", "ORDER", "ASC", "DESC", "LIMIT", "OFFSET",
        "JOIN", "INNER", "LEFT", "RIGHT", "FULL", "ON", "USING",
        "WITH", "UNION", "ALL", "INTERSECT", "EXCEPT",
        "BEGIN", "COMMIT", "ROLLBACK", "SAVEPOINT", "RELEASE"
    )

   private fun insertSpaces(str: String): String {
       var result = str
       operatorsAndSymbols.forEach { op ->
           val regex = Regex.escape(op).toRegex()
           result = result.replace(regex, " $op ")
       }

       return result.replace("\\s+".toRegex(), " ").trim()
   }

    private fun insertAliases(str: List<String>) : List<String> {
        val actions = mutableListOf<(TranslatorContext, String) -> String>()
        val ctx = TranslatorContext(cp, collector)

        str.forEach { w ->
            // skip keyword
            if (keywords.contains(w.uppercase())) actions.add { ctx: TranslatorContext, w: String -> w.uppercase() }

            // replace ASTERISK by some alias ( ... COUNT(vets) FROM Vet vets ...)
            else if (w == "*") actions.add { ctx: TranslatorContext, w: String -> ctx.getSomeAlias() }

            // skip method's arguments
            else if (w.startsWith(":") || w.startsWith("'") || isOperator(w)) actions.add { ctx: TranslatorContext, w: String -> w }

            // is some table
            else if (ctx.isTable(w)) {
                actions.add { ctx: TranslatorContext, w: String -> ctx.addClassName(w) }
            }

            // field
            else {
                actions.add { ctx: TranslatorContext, w: String -> ctx.addPrefix(w) }
            }
        }

        return str.zip(actions).map { (w, action) -> action(ctx, w) }
    }

    fun buildQuery() = insertSpaces(query)
        .split(" ")
        .let { insertAliases(it) }
        .joinToString(" ")
        .replace("! =", "!=")
}
