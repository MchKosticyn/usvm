package machine.interpreter.transformers.springjpa.query

import machine.interpreter.transformers.springjpa.query.expression.bindGroupBy
import machine.interpreter.transformers.springjpa.query.selectfunction.applySelect
import machine.interpreter.transformers.springjpa.query.selectfunction.bindGroupBy
import machine.interpreter.transformers.springjpa.query.selectfunction.getLambdas
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.spring.query.Query

fun Query.collectRowPositions(info: CommonInfo): Map<String, Map<String, Pair<JcField, Int>>> {
    return from?.collectRowPositions(info) ?: mapOf()
}

fun Query.collectAliases(info: CommonInfo): Map<String, String> {
    return from?.collectAliases(info) ?: mapOf()
}

fun Query.getLambdas(info: CommonInfo) =
    listOfNotNull(
        from?.getLambdas(info),
        where?.getLambdas(info),
        select?.getLambdas(info),
        groupBy?.getLambdas(info),
        having?.getLambdas(info)
    ).flatten()

fun Query.genInst(ctx: MethodCtx): JcLocalVar {
    val fromRes = from?.genInst(ctx)!! // TODO: no FROM part
    val filtred = where?.applyWhere(fromRes, ctx) ?: fromRes
    val grouped = groupBy?.applyGroupBy(filtred, ctx) ?: filtred
    val havinged = having?.also { it.predicate.bindGroupBy() }?.applyHaving(grouped, ctx) ?: grouped

    // TODO: no SELECT part
    select?.also { if (groupBy != null) it.bindGroupBy() }
    val selected = select!!.applySelect(havinged, ctx)

    return selected
}
