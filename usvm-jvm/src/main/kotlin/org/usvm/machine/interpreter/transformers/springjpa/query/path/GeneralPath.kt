package org.usvm.machine.interpreter.transformers.springjpa.query.path

import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.expresion.ExpressionCtx

class GeneralPathCtx(
    val path: SimplePathCtx,
    val index: Index?
) {
    class Index(val ix: ExpressionCtx, val cont: GeneralPathCtx?)

    fun isSimple(): Boolean {
        return path.isSimple()
    }

    override fun toString(): String {
        return path.flat()
    }

    fun applyAliases(common: CommonInfo): String {
        return path.applyAliases(common)
    }

    // TODO: you cant indexing in path when in join target or similar

    // TODO: indexing
    fun fullPath(): SimplePathCtx {
        return path
    }

    fun genInst(ctx: MethodCtx): JcLocalVar {
        return if (isSimple()) genObj(ctx) else genField(ctx)
    }

    private fun genObj(ctx: MethodCtx): JcLocalVar {
        return ctx.genObj(path.applyAliases(ctx.common))
    }

    private fun genField(ctx: MethodCtx): JcLocalVar {
        return ctx.genField(ctx.applyAliases(path.root), path.cont)
    }
}
