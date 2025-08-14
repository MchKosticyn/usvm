package machine.interpreter.transformers.springjpa.query

import jpa.generateLambda
import jpa.generateNewWithInit
import jpa.reloadJpaTerm
import machine.interpreter.transformers.springjpa.query.expression.getLambdas
import machine.interpreter.transformers.springjpa.query.expression.toLambda
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.spring.query.Having

fun Having.getOwnMethod(info: CommonInfo) = predicate.toLambda(info)

fun Having.getLambdaVar(ctx: MethodCtx) = with(ctx) {
    genCtx.generateLambda(cp, "${getLambdaName()}_var", getOwnMethod(common))
}

fun Having.applyHaving(tbl: JcLocalVar, ctx: MethodCtx) = with(ctx) {
    genCtx.generateNewWithInit("having_wrap", common.havingType, listOf(tbl, getLambdaVar(this), getMethodArgs()))
}

fun Having.getLambdas(info: CommonInfo) = predicate.getLambdas(info) + getOwnMethod(info)
