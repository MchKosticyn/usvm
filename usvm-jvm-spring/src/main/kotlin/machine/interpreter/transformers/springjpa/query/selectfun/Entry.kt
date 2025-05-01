package machine.interpreter.transformers.springjpa.query.selectfun

import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.path.Path
import machine.interpreter.transformers.springjpa.query.selectfun.SelectFuntion.SelectionCtx
import org.jacodb.api.jvm.cfg.JcLocalVar

class Entry(val path: Path, alias: String?) : SelectionCtx(alias) {
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
