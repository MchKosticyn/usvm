package machine.interpreter.transformers.springjpa.query.specification

import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.SingleLambdable

class GroupBySpec(
    val spec: Specification
) : SingleLambdable(spec) {
    fun getTranslate(ctx: MethodCtx) = spec.getTranslate(ctx)
    fun getTranslateRetType(ctx: MethodCtx) = spec.getTranslateRetType(ctx)
    fun getComparer(ctx: MethodCtx) = spec.getComparer(ctx)
}
