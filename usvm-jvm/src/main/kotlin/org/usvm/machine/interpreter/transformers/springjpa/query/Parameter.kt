package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.usvm.jvm.util.toJcType
import org.usvm.machine.interpreter.transformers.springjpa.query.expresion.Expression
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Param
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Type
import org.usvm.machine.interpreter.transformers.springjpa.toArgument


// Argument from original toplevel-function
abstract class Parameter : Expression() {

    abstract fun position(info: CommonInfo): Int

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val pos = position(ctx.common)
        return access(ctx, pos)
    }

    fun access(ctx: MethodCtx, pos: Int): JcLocalVar {
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

    override val type: Type = Param(this)
}

// Simple name
class Colon(val name: String) : Parameter() {
    override fun position(info: CommonInfo) = info.origMethodArguments[name]!!
}

// ?3
class Positional(val pos: Int) : Parameter() {
    override fun position(info: CommonInfo) = pos
}
