package machine.interpreter.transformers.springjpa.query.specification

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.spring.query.specification.SortSpecification

fun SortSpecification.getLambdas(info: CommonInfo) = spec.getLambdas(info)
fun SortSpecification.getTranslate(ctx: MethodCtx) = spec.getTranslate(ctx)
fun SortSpecification.getTranslateRetType(ctx: MethodCtx) = spec.getTranslateRetType(ctx)
fun SortSpecification.getComparer(ctx: MethodCtx) = spec.getComparer(ctx)
