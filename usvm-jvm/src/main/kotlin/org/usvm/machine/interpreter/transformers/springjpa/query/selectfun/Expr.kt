package org.usvm.machine.interpreter.transformers.springjpa.query.selectfun

import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.ExprOrPredCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.selectfun.SelectFunCtx.SelectionCtx

class Expr(val value: ExprOrPredCtx, alias: String?) : SelectionCtx(alias) {
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        return value.genInst(ctx)
    }
}
