package org.usvm.machine.interpreter.transformers.springjpa.query.join

import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx

abstract class Join {

    open fun getAlias(): Pair<String, String>? = null

    abstract fun positions(info: CommonInfo): List<String>
    abstract fun getLambdas(info: CommonInfo): List<JcMethod>
    abstract fun collectNames(info: CommonInfo): Map<String, List<JcField>>
    abstract fun genJoin(ctx: MethodCtx, name: String, root: JcLocalVar): JcLocalVar
}
