package machine.interpreter.transformers.springjpa.query.specification

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcLocalVar


class ByIdent(val name: String) : Specification() {
    override fun getLambdas(info: CommonInfo) = emptyList<JcMethod>()

    override fun getTranslate(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun getTranslateRetType(ctx: MethodCtx): JcType {
        TODO("Not yet implemented")
    }

    override fun getComparer(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
