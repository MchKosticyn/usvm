package org.usvm.machine.interpreter.transformers.springjpa.query.expresion

import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.path.GeneralPathCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Path
import org.usvm.machine.interpreter.transformers.springjpa.query.type.TypeCtx

class SyntacticPath() : ExpressionCtx() { // TODO:

    override val type: TypeCtx
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class GeneralPath(val path: GeneralPathCtx) : ExpressionCtx() {

    val simplePath = path.fullPath() // TODO: indexing

    override val type = Path(path.fullPath())

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val sPath = path.path
        return if (path.isSimple()) ctx.genObj(sPath.root) else ctx.genField(sPath.root, sPath.cont)
    }

//    private fun genFetch(ctx: MethodCtx, tblInfo: TableInfo.TableWithIdInfo): List<JcLocalVar> {
//        val common = ctx.common
//        return tblInfo.orderedRelations().map { rel ->
//            val relTblName = rel.toTableName(ctx.cp)
//            val tblField = common.databases.fields.single { it.name == relTblName }
//            val v = ctx.newVar(tblField.type)
//            val field = JcFieldRef(null, tblField)
//            ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, v, field) }
//            v
//        }
//    }
}
