package machine.interpreter.transformers.springjpa.query.expresion

import machine.interpreter.transformers.springjpa.query.ExprOrPred
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.Parameter
import machine.interpreter.transformers.springjpa.query.predicate.PredicateCtx
import machine.interpreter.transformers.springjpa.query.type.Param
import machine.interpreter.transformers.springjpa.query.type.Tuple
import machine.interpreter.transformers.springjpa.query.type.Type
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.Select

abstract class Expression : ExprOrPred()

class ParameterExpr(val param: Parameter) : Expression() {

    override val type = Param(param)

    override fun genInst(ctx: MethodCtx) = param.genInst(ctx)
}

class TupleExpr(val elems: List<ExprOrPred>) : Expression() {
    override val type: Type = Tuple(elems.map { it.type })

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Subquery(val query: Select) : Expression() {
    override val type: Type
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

// CASE a WHEN 'a' THEN 1 WHEN 'b' THEN 2 ELSE 3 END
class SimpleCaseList(
    val caseValue: ExprOrPred,
    val branches: List<BranchCtx>,
    val elseBranch: ExprOrPred?
) : Expression() {
    class BranchCtx(val expr: Expression, val value: ExprOrPred)

    override val type: Type = branches.first().value.type

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

// CASE WHEN a == 'a' THEN 1 WHEN a == 'b' THEN 2 ELSE 3 END
class CaseList(val branches: List<BranchCtx>, val elseBranch: ExprOrPred?) : Expression() {
    class BranchCtx(val pred: PredicateCtx, val value: ExprOrPred)

    override val type: Type = branches.first().value.type

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
