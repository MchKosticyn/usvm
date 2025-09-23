package machine.interpreter.transformers.springjpa.query

import jpa.downcastRefTypeIfNeeded
import jpa.generateNewWithInit
import machine.interpreter.transformers.springjpa.query.paramorint.genInst
import machine.interpreter.transformers.springjpa.query.specification.getComparer
import machine.interpreter.transformers.springjpa.query.specification.getLambdas
import machine.interpreter.transformers.springjpa.query.specification.getTranslate
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.int
import org.usvm.spring.query.Order

fun Order.getLambdas(info: CommonInfo) = sorts.flatMap { it.getLambdas(info) }

fun Order.applyOrder(
    tbl: JcLocalVar,
    ctx: MethodCtx
) = with(ctx) {
    sorts.foldIndexed(tbl) { ix, acc, spec ->
        val translate = spec.getTranslate(this)
        val comparer = spec.getComparer(this)
        val lim =
            if (ix + 1 != sorts.size || limit == null) JcInt(-1, cp.int)
            else genCtx.downcastRefTypeIfNeeded(cp, ctx.getVarName(), limit!!.genInst(this))
        val off =
            if (ix + 1 != sorts.size || offset == null) JcInt(0, cp.int)
            else genCtx.downcastRefTypeIfNeeded(cp, ctx.getVarName(), offset!!.genInst(this))
        val dir = JcBool(spec.isAscending, cp.boolean)
        val nulls = JcBool(spec.isNullsLast, cp.boolean)
        val args = listOf(acc, lim, off, dir, nulls, translate, comparer, getMethodArgs())
        genCtx.generateNewWithInit("sort_wrap_$ix", common.orderType, args)
    }
}
