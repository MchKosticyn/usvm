package machine.interpreter.transformers.springjpa.query.sortspec

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar


class ByIdent(val name: String) : SortSpec() {
    override fun getLambdas(info: CommonInfo): List<JcMethod> {
        TODO("Not yet implemented")
    }

    override fun getTranslate(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun getComparer(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
