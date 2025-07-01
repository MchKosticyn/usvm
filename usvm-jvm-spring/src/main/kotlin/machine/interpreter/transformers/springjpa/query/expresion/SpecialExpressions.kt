package machine.interpreter.transformers.springjpa.query.expresion

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.DoubleLambdable
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.Parameter
import machine.interpreter.transformers.springjpa.query.type.Param
import machine.interpreter.transformers.springjpa.query.type.SqlType
import machine.interpreter.transformers.springjpa.query.type.Tuple
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.Select

class ParameterExpr(val param: Parameter) : SingleArgumentExpression(param) {
    override val type = Param(param)
    override fun genInst(ctx: MethodCtx) = param.genInst(ctx)
}

class TupleExpr(val elems: List<Expression>) : ManyArgumentExpression(elems) {
    override val type: SqlType = Tuple(elems.map { it.type })

    override fun getLambdas(info: CommonInfo) = elems.flatMap { it.getLambdas(info) }

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Subquery(val query: Select) : Expression() {
    override val type: SqlType
        get() = TODO("Not yet implemented")

    override fun getLambdas(info: CommonInfo) = query.getLambdas(info)

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

// CASE a WHEN 'a' THEN 1 WHEN 'b' THEN 2 ELSE 3 END
class SimpleCaseList(
    val caseValue: Expression,
    val branches: List<BranchCtx>,
    val elseBranch: Expression?
) : Expression() {
    class BranchCtx(val expr: Expression, val value: Expression) : DoubleLambdable(expr, value)

    override val type: SqlType = branches.first().value.type

    override fun getLambdas(info: CommonInfo): List<JcMethod> {
        val lambdas = branches.flatMap { it.getLambdas(info) } + caseValue.getLambdas(info)
        return elseBranch?.let { lambdas + it.getLambdas(info) } ?: lambdas
    }

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

// CASE WHEN a == 'a' THEN 1 WHEN a == 'b' THEN 2 ELSE 3 END
class CaseList(val branches: List<BranchCtx>, val elseBranch: Expression?) : Expression() {
    class BranchCtx(val pred: Expression, val value: Expression) : DoubleLambdable(pred, value)

    override val type: SqlType = branches.first().value.type

    override fun getLambdas(info: CommonInfo) =
        (listOfNotNull(elseBranch?.getLambdas(info)) + branches.map { it.getLambdas(info) }).flatten()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
