package machine.interpreter.transformers.springjpa.query.selectfun

import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.function.InstCtx
import machine.interpreter.transformers.springjpa.query.selectfun.SelectFuntion.SelectionCtx
import org.jacodb.api.jvm.cfg.JcLocalVar

class Instance(val inst: InstCtx, alias: String?) : SelectionCtx(alias) { // TODO
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
