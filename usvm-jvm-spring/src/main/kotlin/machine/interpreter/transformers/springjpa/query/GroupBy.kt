package machine.interpreter.transformers.springjpa.query

import jpa.generateNewWithInit
import jpa.putValuesWithSameTypeToArray
import machine.interpreter.transformers.springjpa.query.specification.getComparer
import machine.interpreter.transformers.springjpa.query.specification.getLambdas
import machine.interpreter.transformers.springjpa.query.specification.getTranslate
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.spring.query.GroupBy

fun GroupBy.getLambdas(info: CommonInfo) = specs.flatMap { it.getLambdas(info) }

fun GroupBy.applyGroupBy(tbl: JcLocalVar, ctx: MethodCtx) = with(ctx) {
    val translates = specs.map { it.getTranslate(this) }
        .let { genCtx.putValuesWithSameTypeToArray(cp, "translates", it) }
    val comparers = specs.map { it.getComparer(this) }
        .let { genCtx.putValuesWithSameTypeToArray(cp, "comparers", it) }

    val args = listOf(tbl, translates, comparers, getMethodArgs())
    genCtx.generateNewWithInit("group_by_wrap", common.groupByType, args)
}
