package machine.interpreter.transformers.springjpa.query.paramorint

import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expression.genInst
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.spring.query.paramorint.AParamOrInt
import org.usvm.spring.query.paramorint.IParamOrIntVisitor
import org.usvm.spring.query.paramorint.Num
import org.usvm.spring.query.paramorint.Param

fun AParamOrInt.genInst(ctx: MethodCtx): JcLocalVar = this.accept(paramOrIntGenInstVisitor, ctx)
private val paramOrIntGenInstVisitor = object : IParamOrIntVisitor<JcLocalVar, MethodCtx> {
    override fun visit(child: Num, ctx: MethodCtx) = with(ctx) {
        val v = newVar(common.integerType)
        val num = JcInt(child.value, common.integerType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, num) }
        v
    }

    override fun visit(child: Param, ctx: MethodCtx) = child.param.genInst(ctx)
}
