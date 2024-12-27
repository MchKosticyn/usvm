package org.usvm.machine.interpreter.transformers.springjpa.query.expresion

import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.SelectCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.ExprOrPredCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.Parameter
import org.usvm.machine.interpreter.transformers.springjpa.query.predicate.PredicateCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Param
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Tuple
import org.usvm.machine.interpreter.transformers.springjpa.query.type.TypeCtx

abstract class ExpressionCtx : ExprOrPredCtx()

class ParamExpr(val param: Parameter) : ExpressionCtx() {

    override val type = Param(param)

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        return param.genInst(ctx)
    }
}

class TupleExpr(val elems: List<ExprOrPredCtx>) : ExpressionCtx() {
    override val type: TypeCtx = Tuple(elems.map { it.type })

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Subquery(val query: SelectCtx) : ExpressionCtx() {
    override val type: TypeCtx
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

// CASE a WHEN 'a' THEN 1 WHEN 'b' THEN 2 ELSE 3 END
class SimpleCaseList(
    val caseValue: ExprOrPredCtx,
    val branches: List<BranchCtx>,
    val elseBranch: ExprOrPredCtx?
) : ExpressionCtx() {
    class BranchCtx(val expr: ExpressionCtx, val value: ExprOrPredCtx)

    override val type: TypeCtx = branches.first().value.type

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

// CASE WHEN a == 'a' THEN 1 WHEN a == 'b' THEN 2 ELSE 3 END
class CaseList(val branches: List<BranchCtx>, val elseBranch: ExprOrPredCtx?) : ExpressionCtx() {
    class BranchCtx(val pred: PredicateCtx, val value: ExprOrPredCtx)

    override val type: TypeCtx = branches.first().value.type

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
