package machine.interpreter.transformers.springjpa.query

import machine.interpreter.transformers.springjpa.generateNewWithInit
import machine.interpreter.transformers.springjpa.putValuesWithSameTypeToArray
import machine.interpreter.transformers.springjpa.query.specification.GroupBySpec
import machine.interpreter.transformers.springjpa.toJavaClass
import org.jacodb.api.jvm.cfg.JcLocalVar

class GroupBy(val specs: List<GroupBySpec>): ManyLambdable(specs) {

    fun applyGroupBy(tbl: JcLocalVar, ctx: MethodCtx) = with(ctx) {
        val translates = specs.map { it.getTranslate(this) }
            .let { genCtx.putValuesWithSameTypeToArray(cp, "translates", it) }
        val comparers = specs.map { it.getComparer(this) }
            .let { genCtx.putValuesWithSameTypeToArray(cp, "comparers", it) }

        val args = listOf(tbl, translates, comparers, getMethodArgs())
        genCtx.generateNewWithInit("group_by_wrap", common.groupByType, args)
    }
}
