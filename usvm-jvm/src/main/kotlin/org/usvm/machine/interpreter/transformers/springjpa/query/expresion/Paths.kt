package org.usvm.machine.interpreter.transformers.springjpa.query.expresion

import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.path.GeneralPath
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Path
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Type

class SyntacticPath() : Expression() {

    override val type: Type
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class ExprPath(val path: GeneralPath) : Expression() {

    val simplePath = path.fullPath() // TODO: indexing

    override val type = Path(path.fullPath())

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val sPath = path.path
        return if (path.isSimple()) ctx.genObj(sPath.root) else ctx.genField(sPath.root, sPath.cont)
    }
}
