package machine.interpreter.transformers.springjpa.query

import machine.interpreter.transformers.springjpa.generateLambda
import machine.interpreter.transformers.springjpa.generateNewWithInit
import machine.interpreter.transformers.springjpa.query.expresion.Expression
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar

class Having(val predicate: Expression): SingleWithOwnLambdable(predicate) {
    override fun getOwnMethod(info: CommonInfo) = predicate.toLambda(info)

    fun getLambdaVar(ctx: MethodCtx) = with(ctx) {
        genCtx.generateLambda(cp, "${getLambdaName()}_var", getOwnMethod(common))
    }

    fun applyHaving(tbl: JcLocalVar, ctx: MethodCtx) = with(ctx) {
        genCtx.generateNewWithInit("having_wrap", common.havingType, listOf(tbl, getLambdaVar(this), getMethodArgs()))
    }
}
