package machine.interpreter.transformers.springjpa.query

import machine.interpreter.transformers.springjpa.query.join.collectNames
import machine.interpreter.transformers.springjpa.query.join.genJoin
import machine.interpreter.transformers.springjpa.query.join.getAlias
import machine.interpreter.transformers.springjpa.query.join.getLambdas
import machine.interpreter.transformers.springjpa.query.table.collectNames
import machine.interpreter.transformers.springjpa.query.table.genInst
import machine.interpreter.transformers.springjpa.query.table.getAlias
import machine.interpreter.transformers.springjpa.query.table.getLambdas
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.spring.query.TableWithJoins

fun TableWithJoins.getLambdas(info: CommonInfo) = joins.flatMap { it.getLambdas(info) } + root.getLambdas(info)

fun TableWithJoins.getAlises(info: CommonInfo): Map<String, String> {
    val aliases = joins.mapNotNull { it.getAlias() }.toMutableList()
    root.getAlias(info)?.also { aliases.add(it) }
    return aliases.associate { p -> p }
}

fun TableWithJoins.collectNames(info: CommonInfo): Map<String, List<JcField>> {
    val rootNames = root.collectNames(info)
    return joins.fold(rootNames) { acc, join -> acc + join.collectNames(info) }
}

fun TableWithJoins.genInst(ctx: MethodCtx): JcLocalVar {
    val rootTbl = root.genInst(ctx)
    return joins.foldIndexed(rootTbl) { ix, acc, join -> join.genJoin(ctx, "\$j_$ix", acc) }
}
