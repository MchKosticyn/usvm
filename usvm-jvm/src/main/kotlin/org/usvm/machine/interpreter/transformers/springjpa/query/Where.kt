package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.generateLambda
import org.usvm.machine.interpreter.transformers.springjpa.query.predicate.PredicateCtx

class Where(
    val predicate: PredicateCtx
) {

    fun getLambdas(info: CommonInfo) = listOf(getMethod(info))

    fun getMethod(info: CommonInfo) = predicate.toLambda(info)

    fun getLambdaVar(ctx: MethodCtx): JcLocalVar {
        val method = getMethod(ctx.common)
        val lambda = ctx.genCtx.generateLambda(ctx.cp, "${ctx.getLambdaName()}_var", method)
        return lambda
    }
}
