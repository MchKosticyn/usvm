package machine.interpreter.transformers.springjpa.query

import machine.interpreter.transformers.springjpa.query.selectfun.SelectFunction
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar

class Query(): Lambdable() {

    private var from: From? = null
    private var where: Where? = null
    private var select: SelectFunction? = null
    private var groupBy: GroupBy? = null
    private var having: Having? = null

    constructor(_from: From?, _where: Where?, _select: SelectFunction?, _groupBy: GroupBy?, _having: Having?) : this() {
        from = _from
        where = _where
        select = _select
        groupBy = _groupBy
        having = _having

        select?.also { if (groupBy != null) it.bindGroupBy() }
    }

    fun collectRowPositions(info: CommonInfo): Map<String, Map<String, Pair<JcField, Int>>> {
        return from?.collectRowPositions(info) ?: mapOf()
    }

    fun collectAliases(info: CommonInfo): Map<String, String> {
        return from?.collectAliases(info) ?: mapOf()
    }

    override fun getLambdas(info: CommonInfo): List<JcMethod> {
        return listOfNotNull(
            from?.getLambdas(info),
            where?.getLambdas(info),
            select?.getLambdas(info),
            groupBy?.getLambdas(info),
            having?.getLambdas(info)
        ).flatten()
    }

    fun genInst(ctx: MethodCtx): JcLocalVar {
        val fromRes = from?.genInst(ctx)!! // TODO: no FROM part
        val filtred = where?.applyWhere(fromRes, ctx) ?: fromRes
        val grouped = groupBy?.applyGroupBy(filtred, ctx) ?: filtred
        val havinged = having?.applyHaving(grouped, ctx) ?: grouped

        // TODO: no SELECT part
        val selected = select!!.applySelect(havinged, ctx)

        return selected
    }
}
