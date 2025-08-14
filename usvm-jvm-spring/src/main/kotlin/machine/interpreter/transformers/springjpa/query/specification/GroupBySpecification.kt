package machine.interpreter.transformers.springjpa.query.specification

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.spring.query.specification.GroupBySpecification

fun GroupBySpecification.getLambdas(info: CommonInfo) = spec.getLambdas(info)
fun GroupBySpecification.getTranslate(ctx: MethodCtx) = spec.getTranslate(ctx)
fun GroupBySpecification.getTranslateRetType(ctx: MethodCtx) = spec.getTranslateRetType(ctx)
fun GroupBySpecification.getComparer(ctx: MethodCtx) = spec.getComparer(ctx)
