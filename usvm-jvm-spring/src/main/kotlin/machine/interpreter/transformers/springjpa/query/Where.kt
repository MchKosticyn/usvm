package machine.interpreter.transformers.springjpa.query

import machine.interpreter.transformers.springjpa.generateLambda
import machine.interpreter.transformers.springjpa.generateNewWithInit
import machine.interpreter.transformers.springjpa.query.expresion.Expression
import org.jacodb.api.jvm.cfg.JcLocalVar

class Where(val predicate: Expression) : SingleWithOwnLambdable(predicate) {
    override fun getOwnMethod(info: CommonInfo) = predicate.toLambda(info)

    fun getLambdaVar(ctx: MethodCtx) = with(ctx) {
        genCtx.generateLambda(cp, "${getLambdaName()}_var", getOwnMethod(common))
    }

    fun applyWhere(tbl: JcLocalVar, ctx: MethodCtx) = with(ctx) {
        genCtx.generateNewWithInit("res_filtred", common.filterType, listOf(tbl, getLambdaVar(this), getMethodArgs()))
    }
}
