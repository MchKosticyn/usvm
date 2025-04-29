package machine.interpreter.transformers.springjpa.query

import machine.interpreter.transformers.springjpa.JAVA_CLASS
import machine.interpreter.transformers.springjpa.generateNewWithInit
import machine.interpreter.transformers.springjpa.query.selectfun.SelectFuntion
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcClassConstant
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.findType

class Query(
    val from: From?,
    val where: Where?,
    val select: SelectFuntion?
) {

    fun collectRowPositions(info: CommonInfo): Map<String, Map<String, Pair<JcField, Int>>> {
        return from?.collectRowPositions(info) ?: mapOf()
    }

    fun collectAliases(info: CommonInfo): Map<String, String> {
        return from?.collectAliases(info) ?: mapOf()
    }

    fun getLambdas(info: CommonInfo): List<JcMethod> {
        return listOf(
            from?.getLambdas(info) ?: listOf(),
            where?.getLambdas(info) ?: listOf(),
            select?.getLambdas(info) ?: listOf()
        ).flatten()
    }

    fun genInst(ctx: MethodCtx, orders: List<Order>): JcLocalVar {
        val methodArgs = ctx.getMethodArgs()
        val fromRes = from?.genInst(ctx)!! // TODO: no FROM part
        val whereFun = where?.getLambdaVar(ctx)
        val selFun = select?.getLambdaVar(ctx)!! // TODO: no SELECT part

        val filtred = whereFun?.let {
            ctx.genCtx.generateNewWithInit("res_filtred", ctx.common.filterType, listOf(fromRes, it, methodArgs))
        } ?: fromRes

        val ordered = orders.fold(filtred) { p, order -> order.applyOrder(p, ctx) }

        val retType = ctx.cp.findType(ctx.common.origReturnGeneric)
        val classType = ctx.cp.findType(JAVA_CLASS)
        val typeVar = ctx.genCtx.nextLocalVar("type", classType)
        val type = JcClassConstant(retType, classType)
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, typeVar, type) }

        val mapped = ctx.genCtx
            .generateNewWithInit("mapped", ctx.common.mapperType, listOf(ordered, selFun, typeVar, methodArgs))
        val afterDistinct =
            if (select.isDistinct)
                ctx.genCtx.generateNewWithInit("dis", ctx.common.distinctType, listOf(mapped))
            else mapped

        return afterDistinct
    }
}
