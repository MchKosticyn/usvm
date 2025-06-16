package machine.interpreter.transformers.springjpa.query

import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import machine.interpreter.transformers.springjpa.generateNewWithInit
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.cfg.JcLocalVar

class From(val tables: List<TableWithJoins>): ManyLambdable(tables) {

    fun collectRowPositions(info: CommonInfo): Map<String, Map<String, Pair<JcField, Int>>> =
        tables.map { it.collectNames(info) }
            .fold(mapOf()) { acc, map ->
                val updatedMap = map.toPersistentMap().mapValues { (_, fields) ->
                    fields.mapIndexed { ix, field ->
                        field to ix + acc.size to field.name
                    }.associate { (p, name) ->
                        name to p
                    }
                }
                acc + updatedMap
            }

    fun collectAliases(info: CommonInfo): Map<String, String> =
        tables.map { it.getAlises(info) }.fold(mapOf()) { acc, map -> acc + map }

    // FROM Foo, Bar, Baz -> FROM Foo JOIN Bar JOIN Baz
    fun genInst(ctx: MethodCtx): JcLocalVar {
        val root = tables.first().genInst(ctx)
        return tables.toPersistentList().removeAt(0).foldIndexed(root) { ix, acc, tbl ->
            val next = tbl.genInst(ctx)
            ctx.genCtx.generateNewWithInit("\$j_$ix", ctx.common.joinType, listOf(acc, next))
        }
    }
}
