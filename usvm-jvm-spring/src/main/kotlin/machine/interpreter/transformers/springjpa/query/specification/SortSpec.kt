package machine.interpreter.transformers.springjpa.query.specification

import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.SingleLambdable

class SortSpec(
    val spec: Specification,
    var isAscending: Boolean = true, // true -  ASC, false - DESC
    var isNullsLast: Boolean = true // true - LAST, false - FIRST
): SingleLambdable(spec) {
    fun getTranslate(ctx: MethodCtx) = spec.getTranslate(ctx)
    fun getTranslateRetType(ctx: MethodCtx) = spec.getTranslateRetType(ctx)
    fun getComparer(ctx: MethodCtx) = spec.getComparer(ctx)
}
