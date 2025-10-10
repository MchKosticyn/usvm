package machine.interpreter.transformers.springjpa.query

import jpa.FUNCTION2
import jpa.generateNewWithInit
import jpa.putValuesWithSameTypeToArray
import machine.interpreter.transformers.springjpa.query.specification.getComparer
import machine.interpreter.transformers.springjpa.query.specification.getLambdas
import machine.interpreter.transformers.springjpa.query.specification.getTranslate
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.findType
import org.usvm.spring.query.GroupBy

fun GroupBy.getLambdas(info: CommonInfo) = specs.flatMap { it.getLambdas(info) }

fun GroupBy.applyGroupBy(tbl: JcLocalVar, ctx: MethodCtx) = with(ctx) {
    val biFunction = cp.findType(FUNCTION2) as JcClassType
    val translates = specs.map { it.getTranslate(this) }
        .let { genCtx.putValuesWithSameTypeToArray(cp, "translates", it, biFunction) }
    val comparers = specs.map { it.getComparer(this) }
        .let { genCtx.putValuesWithSameTypeToArray(cp, "comparers", it, biFunction) }

    val args = listOf(tbl, translates, comparers, getMethodArgs())
    genCtx.generateNewWithInit("group_by_wrap", common.groupByType, args)
}
