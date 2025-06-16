package machine.interpreter.transformers.springjpa.query

import machine.interpreter.transformers.springjpa.generateNewWithInit
import machine.interpreter.transformers.springjpa.query.specification.SortSpec
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.int

class Order(
    val sorts: List<SortSpec>,
    private var limit: ParamOrInt? = null,
    private var offset: ParamOrInt? = null
): ManyLambdable(sorts) {
    fun setLimit(lim: ParamOrInt?) {
        limit = lim
    }

    fun setOffset(off: ParamOrInt?) {
        offset = off
    }

    fun applyOrder(
        tbl: JcLocalVar,
        ctx: MethodCtx
    ) = with(ctx) {
        sorts.foldIndexed(tbl) { ix, acc, spec ->
            val translate = spec.getTranslate(this)
            val comparer = spec.getComparer(this)
            val lim = if (ix + 1 != sorts.size || limit == null) JcInt(-1, cp.int) else limit!!.genInst(this)
            val off = if (ix + 1 != sorts.size || offset == null) JcInt(0, cp.int) else offset!!.genInst(this)
            val dir = JcBool(spec.isAscending, cp.boolean)
            val nulls = JcBool(spec.isNullsLast, cp.boolean)
            val args = listOf(acc, lim, off, dir, nulls, translate, comparer, getMethodArgs())
            genCtx.generateNewWithInit("sort_wrap_$ix", common.orderType, args)
        }
    }
}
