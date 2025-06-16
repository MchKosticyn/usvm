package machine.interpreter.transformers.springjpa.query.expresion

import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.path.GeneralPath
import machine.interpreter.transformers.springjpa.query.type.Path
import machine.interpreter.transformers.springjpa.query.type.SqlType
import org.jacodb.api.jvm.cfg.JcLocalVar

class SyntacticPath(): NoLambdaExpression() {

    override val type: SqlType
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class ExprPath(val path: GeneralPath): NoLambdaExpression() {

    val simplePath = path.fullPath() // TODO: indexing

    override val type = Path(path.fullPath())

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val sPath = path.path
        return if (path.isSimple())
            ctx.genObj(sPath.root, isGrouped)
        else
            ctx.genField(sPath.root, sPath.cont, isGrouped)
    }
}
