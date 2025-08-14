package machine.interpreter.transformers.springjpa.query

import jpa.generateLambda
import jpa.generateNewWithInit
import machine.interpreter.transformers.springjpa.query.expression.getLambdas
import machine.interpreter.transformers.springjpa.query.expression.toLambda
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.spring.query.Where

fun Where.getLambdas(info: CommonInfo) = predicate.getLambdas(info) + getOwnMethod(info)

fun Where.getOwnMethod(info: CommonInfo) = predicate.toLambda(info)

fun Where.getLambdaVar(ctx: MethodCtx) = with(ctx) {
    genCtx.generateLambda(cp, "${getLambdaName()}_var", getOwnMethod(common))
}

fun Where.applyWhere(tbl: JcLocalVar, ctx: MethodCtx) = with(ctx) {
    genCtx.generateNewWithInit("res_filtred", common.filterType, listOf(tbl, getLambdaVar(this), getMethodArgs()))
}
