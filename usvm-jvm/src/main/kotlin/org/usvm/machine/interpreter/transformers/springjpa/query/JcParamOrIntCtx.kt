package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.cfg.JcLocalVar

abstract class ParamOrInt {

    abstract fun genInst(ctx: MethodCtx): JcLocalVar

    class Param(val param: Parameter) : ParamOrInt() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Num(val value: Int) : ParamOrInt() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }
}
