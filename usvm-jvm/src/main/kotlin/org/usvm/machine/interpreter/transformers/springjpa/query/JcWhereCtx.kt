package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.generateLambda
import org.usvm.machine.interpreter.transformers.springjpa.query.predicate.PredicateCtx

class WhereCtx(
    val predicate: PredicateCtx
) {

    fun getLambdas(info: CommonInfo): List<JcMethod> {
        return listOf(getMethod(info))
    }

    fun getMethod(info: CommonInfo): JcMethod {
        return predicate.toLambda(info)
    }

    fun getLambdaVar(ctx: MethodCtx): JcLocalVar {
        val method = getMethod(ctx.common)
        val lambda = ctx.genCtx.generateLambda(ctx.cp, "${ctx.getLambdaName()}Var", method)
        return lambda
    }
}
