package machine.interpreter.transformers.springjpa.query.expression

import machine.interpreter.transformers.springjpa.query.function.getType
import machine.interpreter.transformers.springjpa.query.path.fullPath
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
import org.usvm.spring.query.type.AType
import org.usvm.spring.query.type.TNull
import org.usvm.spring.query.type.TParam
import org.usvm.spring.query.type.TPath
import org.usvm.spring.query.type.primitive.TBigDecimal
import org.usvm.spring.query.type.primitive.TBigInt
import org.usvm.spring.query.type.primitive.TBinary
import org.usvm.spring.query.type.primitive.TBool
import org.usvm.spring.query.type.primitive.TDouble
import org.usvm.spring.query.type.primitive.TFloat
import org.usvm.spring.query.type.primitive.TInt
import org.usvm.spring.query.type.primitive.TList
import org.usvm.spring.query.type.primitive.TLong
import org.usvm.spring.query.type.primitive.TString

fun AExpression.type(): AType = this.accept(expressionTypeVisitor, null)
private val expressionTypeVisitor = object : IExpressionVisitor<AType, Any?> {
    override fun visit(child: BinOperator, ctx: Any?) = child.left.type()

    override fun visit(child: FromDuration, ctx: Any?): AType { TODO("Not yet implemented") }

    override fun visit(child: Id, ctx: Any?): AType { TODO("Not yet implemented") }

    override fun visit(child: Minus, ctx: Any?) = child.expr.type()

    override fun visit(child: NaturalId, ctx: Any?): AType {
        TODO("Not yet implemented")
    }

    override fun visit(child: ToDuration, ctx: Any?): AType {
        TODO("Not yet implemented")
    }

    override fun visit(child: TypeOfParameter, ctx: Any?): AType {
        TODO("Not yet implemented")
    }

    override fun visit(child: TypeOfPath, ctx: Any?): AType {
        TODO("Not yet implemented")
    }

    override fun visit(child: Version, ctx: Any?): AType {
        TODO("Not yet implemented")
    }

    override fun visit(child: LBigDecimal, ctx: Any?) = TBigDecimal()

    override fun visit(child: LBigInt, ctx: Any?) = TBigInt()

    override fun visit(child: LBinary, ctx: Any?) = TBinary()

    override fun visit(child: LBool, ctx: Any?) = TBool()

    override fun visit(child: LDouble, ctx: Any?) = TDouble()

    override fun visit(child: LFloat, ctx: Any?) = TFloat()

    override fun visit(child: LInt, ctx: Any?) = TInt()

    override fun visit(child: LLong, ctx: Any?) = TLong()

    override fun visit(child: LNull, ctx: Any?) = TNull()

    override fun visit(child: LString, ctx: Any?) = TString()

    override fun visit(child: LTime, ctx: Any?): AType {
        TODO("Not yet implemented")
    }

    override fun visit(child: ExprPath, ctx: Any?) = TPath(child.path.fullPath())

    override fun visit(child: SyntacticPath, ctx: Any?): AType {
        TODO("Not yet implemented")
    }

    override fun visit(child: CaseList, ctx: Any?) = child.branches.first().value.type()

    override fun visit(child: SimpleCaseList, ctx: Any?) = child.branches.first().value.type()

    override fun visit(child: Parameter, ctx: Any?) = TParam(child.param)

    override fun visit(child: Subquery, ctx: Any?): AType {
        TODO("Not yet implemented")
    }

    override fun visit(child: Tuple, ctx: Any?): AType {
        TODO("Not yet implemented")
    }

    override fun visit(child: Instance, ctx: Any?): AType {
        TODO("Not yet implemented")
    }

    override fun visit(child: SqlFunction, ctx: Any?) = child.getType()

    override fun visit(child: Colon, ctx: Any?) = TParam(child)

    override fun visit(child: Positional, ctx: Any?) = TParam(child)

    override fun visit(child: Compare, ctx: Any?) = TBool()

    override fun visit(child: ExistCollection, ctx: Any?) = TBool()

    override fun visit(child: InFunction, ctx: Any?) = TBool()

    override fun visit(child: Between, ctx: Any?) = TBool()

    override fun visit(child: Exist, ctx: Any?) = TBool()

    override fun visit(child: IsDistinct, ctx: Any?) = TBool()

    override fun visit(child: IsEmpty, ctx: Any?) = TBool()

    override fun visit(child: IsNull, ctx: Any?) = TBool()

    override fun visit(child: IsTrue, ctx: Any?) = TBool()

    override fun visit(child: Like, ctx: Any?) = TBool()

    override fun visit(child: Member, ctx: Any?) = TBool()

    override fun visit(child: And, ctx: Any?) = TBool()

    override fun visit(child: Not, ctx: Any?) = TBool()

    override fun visit(child: Or, ctx: Any?) = TBool()

    override fun visit(child: ListIn, ctx: Any?) = TList()

    override fun visit(child: ParameterIn, ctx: Any?) = TList()
}
