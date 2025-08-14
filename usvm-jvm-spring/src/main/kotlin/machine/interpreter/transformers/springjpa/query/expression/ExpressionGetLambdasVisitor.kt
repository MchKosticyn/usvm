package machine.interpreter.transformers.springjpa.query.expression

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.function.getOwnMethod
import machine.interpreter.transformers.springjpa.query.getLambdas
import org.jacodb.api.jvm.JcMethod
import org.usvm.spring.query.expression.AExpression
import org.usvm.spring.query.expression.IExpressionVisitor
import org.usvm.spring.query.expression.functions.BinOperator
import org.usvm.spring.query.expression.functions.FromDuration
import org.usvm.spring.query.expression.functions.Id
import org.usvm.spring.query.expression.functions.Minus
import org.usvm.spring.query.expression.functions.NaturalId
import org.usvm.spring.query.expression.functions.ToDuration
import org.usvm.spring.query.expression.functions.TypeOfParameter
import org.usvm.spring.query.expression.functions.TypeOfPath
import org.usvm.spring.query.expression.functions.Version
import org.usvm.spring.query.expression.literals.LBigDecimal
import org.usvm.spring.query.expression.literals.LBigInt
import org.usvm.spring.query.expression.literals.LBinary
import org.usvm.spring.query.expression.literals.LBool
import org.usvm.spring.query.expression.literals.LDouble
import org.usvm.spring.query.expression.literals.LFloat
import org.usvm.spring.query.expression.literals.LInt
import org.usvm.spring.query.expression.literals.LLong
import org.usvm.spring.query.expression.literals.LNull
import org.usvm.spring.query.expression.literals.LString
import org.usvm.spring.query.expression.literals.LTime
import org.usvm.spring.query.expression.paths.ExprPath
import org.usvm.spring.query.expression.paths.SyntacticPath
import org.usvm.spring.query.expression.special.Parameter
import org.usvm.spring.query.expression.special.Subquery
import org.usvm.spring.query.expression.special.Tuple
import org.usvm.spring.query.expression.special.cases.CaseList
import org.usvm.spring.query.expression.special.cases.SimpleCaseList
import org.usvm.spring.query.function.Instance
import org.usvm.spring.query.function.SqlFunction
import org.usvm.spring.query.parameter.Colon
import org.usvm.spring.query.parameter.Positional
import org.usvm.spring.query.predicate.And
import org.usvm.spring.query.predicate.Not
import org.usvm.spring.query.predicate.Or
import org.usvm.spring.query.predicate.functions.Between
import org.usvm.spring.query.predicate.functions.Exist
import org.usvm.spring.query.predicate.functions.InFunction.InFunction
import org.usvm.spring.query.predicate.functions.InFunction.ListIn
import org.usvm.spring.query.predicate.functions.InFunction.ParameterIn
import org.usvm.spring.query.predicate.functions.IsDistinct
import org.usvm.spring.query.predicate.functions.IsEmpty
import org.usvm.spring.query.predicate.functions.IsNull
import org.usvm.spring.query.predicate.functions.IsTrue
import org.usvm.spring.query.predicate.functions.Like
import org.usvm.spring.query.predicate.functions.Member
import org.usvm.spring.query.predicate.functions.compare.Compare
import org.usvm.spring.query.predicate.functions.existcollection.ExistCollection

fun AExpression.getLambdas(info: CommonInfo): List<JcMethod> = this.accept(expressionGetLambdasVisitor, info)
private val expressionGetLambdasVisitor = object : IExpressionVisitor<List<JcMethod>, CommonInfo> {
    override fun visit(child: BinOperator, ctx: CommonInfo) =
        child.left.getLambdas(ctx) + child.right.getLambdas(ctx)

    override fun visit(child: FromDuration, ctx: CommonInfo) = child.expr.getLambdas(ctx)

    override fun visit(child: Id, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: Minus, ctx: CommonInfo) = child.expr.getLambdas(ctx)

    override fun visit(child: NaturalId, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: ToDuration, ctx: CommonInfo) = child.expr.getLambdas(ctx)

    override fun visit(child: TypeOfParameter, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: TypeOfPath, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: Version, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: LBigDecimal, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: LBigInt, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: LBinary, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: LBool, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: LDouble, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: LFloat, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: LInt, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: LLong, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: LNull, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: LString, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: LTime, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: ExprPath, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: SyntacticPath, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: CaseList, ctx: CommonInfo) = with(child) {
        branches.flatMap { it.value.getLambdas(ctx) + it.pred.getLambdas(ctx) } +
                (elseBranch?.getLambdas(ctx) ?: emptyList())
    }

    override fun visit(child: SimpleCaseList, ctx: CommonInfo) = with(child) {
        branches.flatMap { it.value.getLambdas(ctx) + it.pred.getLambdas(ctx) } +
                caseValue.getLambdas(ctx) +
                (elseBranch?.getLambdas(ctx) ?: emptyList())
    }

    override fun visit(child: Parameter, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: Subquery, ctx: CommonInfo) = child.getLambdas(ctx)

    override fun visit(child: Tuple, ctx: CommonInfo) =
        child.elems.flatMap { it.getLambdas(ctx) }

    override fun visit(child: Instance, ctx: CommonInfo): List<JcMethod> {
        TODO("Not yet implemented")
    }

    override fun visit(child: SqlFunction, ctx: CommonInfo) = with(child) {
        args.flatMap { it.getLambdas(ctx) } + getOwnMethod(ctx)
    }

    override fun visit(child: Colon, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: Positional, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: Compare, ctx: CommonInfo) = with(child) { left.getLambdas(ctx) + right.getLambdas(ctx) }

    override fun visit(child: ExistCollection, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: InFunction, ctx: CommonInfo) = with(child) { expr.getLambdas(ctx) + list.getLambdas(ctx) }

    override fun visit(child: Between, ctx: CommonInfo) = with(child) {
        expr.getLambdas(ctx) + left.getLambdas(ctx) + right.getLambdas(ctx)
    }

    override fun visit(child: Exist, ctx: CommonInfo) = child.expr.getLambdas(ctx)

    override fun visit(child: IsDistinct, ctx: CommonInfo) = with(child) { expr.getLambdas(ctx) + from.getLambdas(ctx) }

    override fun visit(child: IsEmpty, ctx: CommonInfo) = child.expr.getLambdas(ctx)

    override fun visit(child: IsNull, ctx: CommonInfo) = child.expr.getLambdas(ctx)

    override fun visit(child: IsTrue, ctx: CommonInfo) = child.expr.getLambdas(ctx)

    override fun visit(child: Like, ctx: CommonInfo) = with(child) {
        expr.getLambdas(ctx) + pattern.getLambdas(ctx) + (escape?.getLambdas(ctx) ?: emptyList())
    }

    override fun visit(child: Member, ctx: CommonInfo) = child.expr.getLambdas(ctx)

    override fun visit(child: And, ctx: CommonInfo) = with(child) {
        left.getLambdas(ctx) + right.getLambdas(ctx)
    }

    override fun visit(child: Not, ctx: CommonInfo) = child.predicate.getLambdas(ctx)

    override fun visit(child: Or, ctx: CommonInfo) = with(child) {
        left.getLambdas(ctx) + right.getLambdas(ctx)
    }

    override fun visit(child: ListIn, ctx: CommonInfo) = child.list.getLambdas(ctx)

    override fun visit(child: ParameterIn, ctx: CommonInfo) = child.param.getLambdas(ctx)
}
