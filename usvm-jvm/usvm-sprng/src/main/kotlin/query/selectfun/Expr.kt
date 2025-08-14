package machine.interpreter.transformers.springjpa.query.selectfun

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expresion.Expression
import machine.interpreter.transformers.springjpa.query.selectfun.SelectFunction.SelectionCtx

class Expr(val value: Expression, alias: String?) : SelectionCtx(alias) {
    override fun genInst(ctx: MethodCtx) = value.genInst(ctx)

    override fun getLambdas(info: CommonInfo) = value.getLambdas(info)

    override fun bindGroupBy() {
        value.bindGroupBy()
    }
}
