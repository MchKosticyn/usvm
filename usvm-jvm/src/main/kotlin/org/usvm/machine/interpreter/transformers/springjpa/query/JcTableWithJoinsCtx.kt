package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.join.JoinCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.table.TableCtx

class TableWithJoinsCtx(
    val root: TableCtx,
    val joins: List<JoinCtx>
) {

    fun getLambdas(info: CommonInfo): List<JcMethod> {
        return joins.fold(root.genLambas()) { acc, join -> acc + join.getLambdas(info) }
    }

    fun getAlises(info: CommonInfo): Map<String, String> {
        val aliases = joins.mapNotNull { it.getAlias() }.toMutableList()
        root.getAlisas(info)?.also { aliases.add(it) }
        return aliases.associate { p -> p }
    }

    fun collectNames(info: CommonInfo): Map<String, List<JcField>> {
        val rootNames = root.collectNames(info)
        return joins.fold(rootNames) { acc, join -> acc + join.collectNames(info) }
    }

    fun genInst(ctx: MethodCtx): JcLocalVar {
        val rootTbl = root.genInst(ctx)
        return joins.foldIndexed(rootTbl) { ix, acc, join -> join.genJoin(ctx, "\$j$ix", acc) }
    }
}
