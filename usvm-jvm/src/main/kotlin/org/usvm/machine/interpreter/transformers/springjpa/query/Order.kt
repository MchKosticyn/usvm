package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.int
import org.usvm.machine.interpreter.transformers.springjpa.generateNewWithInit
import org.usvm.machine.interpreter.transformers.springjpa.query.sortspec.SortSpec

class Order(
    val sorts: List<SortSpec>,
    private var limit: ParamOrInt? = null,
    private var offset: ParamOrInt? = null
) {
    fun getLambdas(info: CommonInfo) = sorts.flatMap { it.getLambdas(info) }

    fun setLimit(lim: ParamOrInt?) { limit = lim }

    fun setOffset(off: ParamOrInt?) { offset = off }

    fun applyOrder(
        tbl: JcLocalVar,
        ctx: MethodCtx
    ): JcLocalVar {
        val methodArgs = ctx.getMethodArgs()
        return sorts.foldIndexed(tbl) { ix, acc, spec ->
            val translate = spec.getTranslate(ctx)
            val comparer = spec.getComparer(ctx)
            val lim = if (ix + 1 != sorts.size || limit == null) JcInt(-1, ctx.cp.int) else limit!!.genInst(ctx)
            val off = if (ix + 1 != sorts.size || offset == null) JcInt(0, ctx.cp.int) else offset!!.genInst(ctx)
            val dir = JcBool(spec.isAscending, ctx.cp.boolean)
            val nulls = JcBool(spec.isNullsLast, ctx.cp.boolean)
            val args = listOf(acc, lim, off, dir, nulls, translate, comparer, methodArgs)
            ctx.genCtx.generateNewWithInit("sort_wrap_$ix", ctx.common.orderType, args)
        }
    }

}
