package machine.interpreter.transformers.springjpa.query.path

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expresion.Expression

class GeneralPath(
    val path: SimplePath,
    val index: Index?
) {
    class Index(val ix: Expression, val cont: GeneralPath?)

    fun isSimple() = path.isSimple()

    override fun toString() = path.flat()

    fun applyAliases(common: CommonInfo) = path.applyAliases(common)

    // TODO: indexing (you cant indexing in path when in join target or similar)
    fun fullPath(): SimplePath {
        return path
    }

    fun genInst(ctx: MethodCtx) =
        if (isSimple()) genObj(ctx) else genField(ctx)

    private fun genObj(ctx: MethodCtx) = ctx.genObj(path.applyAliases(ctx.common))

    private fun genField(ctx: MethodCtx) = ctx.genField(ctx.applyAliases(path.root), path.cont)
}
