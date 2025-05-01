package machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar

// int value or argument of original toplevel-function as parameter to LIMIT and OFFSET
abstract class ParamOrInt {

    abstract fun genInst(ctx: MethodCtx): JcLocalVar

    class Param(val param: Parameter) : ParamOrInt() {
        override fun genInst(ctx: MethodCtx) = param.genInst(ctx)
    }

    class Num(val value: Int) : ParamOrInt() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            val v = ctx.newVar(ctx.common.integerType)
            val num = JcInt(value, ctx.common.integerType)
            ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, v, num) }
            return v
        }
    }
}
