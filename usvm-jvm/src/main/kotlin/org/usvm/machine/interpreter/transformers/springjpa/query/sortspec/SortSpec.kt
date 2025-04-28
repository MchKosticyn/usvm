package org.usvm.machine.interpreter.transformers.springjpa.query.sortspec

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx

abstract class SortSpec(
    var isAscending: Boolean = true, // true -  ASC, false - DESC
    var isNullsLast: Boolean = true // true - LAST, false - FIRST
) {

    abstract fun getLambdas(info: CommonInfo): List<JcMethod>
    abstract fun getTranslate(ctx: MethodCtx): JcLocalVar
    abstract fun getComparer(ctx: MethodCtx): JcLocalVar
}
