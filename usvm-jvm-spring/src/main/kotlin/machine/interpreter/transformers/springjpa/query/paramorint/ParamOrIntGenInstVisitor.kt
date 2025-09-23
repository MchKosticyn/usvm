package machine.interpreter.transformers.springjpa.query.paramorint

import jpa.generateStaticCall
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expression.genInst
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.int
import org.usvm.spring.query.paramorint.AParamOrInt
import org.usvm.spring.query.paramorint.IParamOrIntVisitor
import org.usvm.spring.query.paramorint.Num
import org.usvm.spring.query.paramorint.Param

fun AParamOrInt.genInst(ctx: MethodCtx): JcLocalVar = this.accept(paramOrIntGenInstVisitor, ctx)
private val paramOrIntGenInstVisitor = object : IParamOrIntVisitor<JcLocalVar, MethodCtx> {
    override fun visit(child: Num, ctx: MethodCtx) = with(ctx) {
        val v = JcInt(child.value, cp.int)
        genCtx.generateStaticCall(common.names.getVarName(), "valueOf", common.integerType, listOf(v))
    }

    override fun visit(child: Param, ctx: MethodCtx) = child.param.genInst(ctx)
}
