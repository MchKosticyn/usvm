package org.usvm.machine.interpreter.transformers.springjpa.query.selectfun

import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.function.InstCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.selectfun.SelectFuntion.SelectionCtx

class Instance(val inst: InstCtx, alias: String?) : SelectionCtx(alias) { // TODO
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
