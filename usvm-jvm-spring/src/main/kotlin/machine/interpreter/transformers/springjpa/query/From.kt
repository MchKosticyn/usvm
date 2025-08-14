package machine.interpreter.transformers.springjpa.query

import jpa.generateNewWithInit
import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.spring.query.From
import kotlin.collections.plus

fun From.collectRowPositions(info: CommonInfo): Map<String, Map<String, Pair<JcField, Int>>> {
    return tables.map { it.collectNames(info) }
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
}

fun From.collectAliases(info: CommonInfo) =
    tables.map { it.getAlises(info) }.fold(emptyMap<String, String>()) { acc, map -> acc + map }

// FROM Foo, Bar, Baz -> FROM Foo JOIN Bar JOIN Baz
fun From.genInst(ctx: MethodCtx): JcLocalVar {
    val root = tables.first().genInst(ctx)
    return tables.toPersistentList().removeAt(0).foldIndexed(root) { ix, acc, tbl ->
        val next = tbl.genInst(ctx)
        ctx.genCtx.generateNewWithInit("\$j_$ix", ctx.common.joinType, listOf(acc, next))
    }
}

fun From.getLambdas(info: CommonInfo) = tables.flatMap { it.getLambdas(info) }
