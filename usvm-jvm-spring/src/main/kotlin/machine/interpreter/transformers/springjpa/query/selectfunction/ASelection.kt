package machine.interpreter.transformers.springjpa.query.selectfunction

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expression.genInst
import machine.interpreter.transformers.springjpa.query.expression.getLambdas
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.spring.query.selectfun.ASelection
import org.usvm.spring.query.selectfun.Entry
import org.usvm.spring.query.selectfun.Expression
import org.usvm.spring.query.selectfun.ISelectionVisitor
import org.usvm.spring.query.selectfun.Instance
import org.usvm.spring.query.selectfun.JpaSelect

fun ASelection.getLambdas(info: CommonInfo): List<JcMethod> = this.accept(selectionGetLambdasVisitor, info)
private val selectionGetLambdasVisitor = object : ISelectionVisitor<List<JcMethod>, CommonInfo> {
    override fun visit(child: Entry, ctx: CommonInfo): List<JcMethod> {
        TODO("Not yet implemented")
    }

    override fun visit(child: Expression, ctx: CommonInfo) = child.value.getLambdas(ctx)

    override fun visit(child: Instance, ctx: CommonInfo): List<JcMethod> {
        TODO("Not yet implemented")
    }

    override fun visit(child: JpaSelect, ctx: CommonInfo): List<JcMethod> {
        TODO("Not yet implemented")
    }
}

fun ASelection.genInst(ctx: MethodCtx): JcLocalVar = this.accept(selectionGenInstVisitor, ctx)
private val selectionGenInstVisitor = object : ISelectionVisitor<JcLocalVar, MethodCtx> {
    override fun visit(child: Entry, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: Expression, ctx: MethodCtx) = child.value.genInst(ctx)

    override fun visit(child: Instance, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: JpaSelect, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

fun ASelection.bindGroupBy(): Unit = this.accept(selectionBindGroupByVisitor, null)
private val selectionBindGroupByVisitor = object : ISelectionVisitor<Unit, Any?> {
    override fun visit(child: Entry, ctx: Any?) {
        TODO("Not yet implemented")
    }

    override fun visit(child: Expression, ctx: Any?) {
        child.bindGroupBy()
    }

    override fun visit(child: Instance, ctx: Any?) {
        TODO("Not yet implemented")
    }

    override fun visit(child: JpaSelect, ctx: Any?) {
        TODO("Not yet implemented")
    }
}
