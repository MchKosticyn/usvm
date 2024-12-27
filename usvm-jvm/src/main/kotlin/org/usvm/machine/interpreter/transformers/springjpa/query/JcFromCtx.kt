package org.usvm.machine.interpreter.transformers.springjpa.query

import kotlinx.collections.immutable.toPersistentList
import kotlinx.collections.immutable.toPersistentMap
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.generateNewWithInit


class FromCtx(
    val tables: List<TableWithJoinsCtx>
) {

    fun getLambdas(info: CommonInfo): List<JcMethod> {
        return tables.flatMap { it.getLambdas(info) }
    }

    fun collectRowPositions(info: CommonInfo): Map<String, Map<String, Pair<JcField, Int>>> {
        return tables.map { it.collectNames(info) }.fold(mapOf()) { acc, map ->
            val updatedMap = map.toPersistentMap().mapValues { (_, fs) ->
                fs.mapIndexed { ix, field -> field to ix + acc.size to field.name }
                    .associate { (p, name) -> name to p }
            }
            acc + updatedMap
        }
    }

    fun collectAliases(info: CommonInfo): Map<String, String> {
        return tables.map { it.getAlises(info) }.fold(mapOf()) { acc, map -> acc + map }
    }

    // FROM Foo, Bar, Baz -> FROM Foo JOIN Bar JOIN Baz
    fun genInst(ctx: MethodCtx): JcLocalVar {
        val root = tables.first().genInst(ctx)
        return tables.toPersistentList().removeAt(0).foldIndexed(root) { ix, acc, tbl ->
            val next = tbl.genInst(ctx)
            ctx.genCtx.generateNewWithInit("\$j$ix", ctx.common.joinType, listOf(acc, next))
        }
    }
}
