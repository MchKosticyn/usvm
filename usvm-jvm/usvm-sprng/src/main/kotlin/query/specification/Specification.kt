package machine.interpreter.transformers.springjpa.query.specification

import machine.interpreter.transformers.springjpa.query.Lambdable
import machine.interpreter.transformers.springjpa.query.MethodCtx
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcLocalVar

abstract class Specification() : Lambdable() {
    abstract fun getTranslate(ctx: MethodCtx): JcLocalVar
    abstract fun getTranslateRetType(ctx: MethodCtx): JcType
    abstract fun getComparer(ctx: MethodCtx): JcLocalVar
}
