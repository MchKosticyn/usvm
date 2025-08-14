package machine.interpreter.transformers.springjpa.query.parameter

import jpa.toArgument
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.usvm.jvm.util.toJcType
import org.usvm.spring.query.parameter.AParameter
import org.usvm.spring.query.parameter.Colon
import org.usvm.spring.query.parameter.Positional

fun AParameter.access(ctx: MethodCtx, pos: Int): JcLocalVar {
    val args = ctx.method.parameters.getOrNull(1)!!.toArgument
    val vari = ctx.newVar(ctx.cp.objectType)
    val access = JcArrayAccess(args, JcInt(pos, ctx.cp.int), ctx.cp.objectType)
    ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, vari, access) }

    val arg = ctx.common.origMethod.parameters[pos]
    val argType = arg.type.toJcType(ctx.cp)!!
    val casted = ctx.newVar(argType)
    val cast = JcCastExpr(argType, vari)
    ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, casted, cast) }

    return casted
}

fun AParameter.position(info: CommonInfo) = when(this) {
    is Colon -> this.position(info)
    is Positional -> this.position(info)
    else -> error("Unexpected AParameter")
}

fun Colon.position(info: CommonInfo) = info.origMethodArguments[name]!!

fun Positional.position(info: CommonInfo) = pos
