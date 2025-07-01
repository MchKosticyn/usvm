package machine.interpreter.transformers.springjpa.query

import machine.interpreter.transformers.springjpa.query.join.Join
import machine.interpreter.transformers.springjpa.query.table.Table
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.cfg.JcLocalVar

class TableWithJoins(
    val root: Table,
    val joins: List<Join>
) : ManyLambdable(joins + root) {
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
        return joins.foldIndexed(rootTbl) { ix, acc, join -> join.genJoin(ctx, "\$j_$ix", acc) }
    }
}
