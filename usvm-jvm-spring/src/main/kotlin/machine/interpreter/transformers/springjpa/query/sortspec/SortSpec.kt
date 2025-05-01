package machine.interpreter.transformers.springjpa.query.sortspec

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar

abstract class SortSpec(
    var isAscending: Boolean = true, // true -  ASC, false - DESC
    var isNullsLast: Boolean = true // true - LAST, false - FIRST
) {

    abstract fun getLambdas(info: CommonInfo): List<JcMethod>
    abstract fun getTranslate(ctx: MethodCtx): JcLocalVar
    abstract fun getComparer(ctx: MethodCtx): JcLocalVar
}
