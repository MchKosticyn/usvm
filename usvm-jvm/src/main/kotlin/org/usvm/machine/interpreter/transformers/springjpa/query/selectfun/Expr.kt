package org.usvm.machine.interpreter.transformers.springjpa.query.selectfun

import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.ExprOrPred
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.selectfun.SelectFuntion.SelectionCtx

class Expr(val value: ExprOrPred, alias: String?) : SelectionCtx(alias) {
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        return value.genInst(ctx)
    }
}
