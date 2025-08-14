package machine.interpreter.transformers.springjpa.query.join

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar

// deprecated syntax (never documented)
class JpaCollectionJoin : Join() {
    override fun positions(info: CommonInfo): List<String> {
        TODO("Not yet implemented")
    }

    override fun getLambdas(info: CommonInfo): List<JcMethod> {
        TODO("Not yet implemented")
    }

    override fun collectNames(info: CommonInfo): Map<String, List<JcField>> {
        TODO("Not yet implemented")
    }

    override fun genJoin(ctx: MethodCtx, name: String, root: JcLocalVar): JcLocalVar {
        TODO("Not yet implemented")
    }
}
