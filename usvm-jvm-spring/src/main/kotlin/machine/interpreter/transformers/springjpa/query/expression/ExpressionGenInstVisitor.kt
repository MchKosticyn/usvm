package machine.interpreter.transformers.springjpa.query.expression

import jpa.generateNewWithInit
import jpa.generateStaticCall
import jpa.putValuesWithSameTypeToArray
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.function.genAggregatorInst
import machine.interpreter.transformers.springjpa.query.function.genSimpleFuncInst
import machine.interpreter.transformers.springjpa.query.function.generateInst
import machine.interpreter.transformers.springjpa.query.function.isAggregator
import machine.interpreter.transformers.springjpa.query.genInstAndWrapToList
import machine.interpreter.transformers.springjpa.query.parameter.access
import machine.interpreter.transformers.springjpa.query.parameter.position
import machine.interpreter.transformers.springjpa.query.path.isSimple
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcByte
import org.jacodb.api.jvm.cfg.JcDouble
import org.jacodb.api.jvm.cfg.JcFloat
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcLong
import org.jacodb.api.jvm.cfg.JcNullConstant
import org.jacodb.api.jvm.cfg.JcStringConstant
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
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

fun AExpression.genInst(ctx: MethodCtx): JcLocalVar = this.accept(expressionGenInstVisitor, ctx)
private val expressionGenInstVisitor = object : IExpressionVisitor<JcLocalVar, MethodCtx> {
    override fun visit(child: BinOperator, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: FromDuration, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: Id, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: Minus, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: NaturalId, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: ToDuration, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: TypeOfParameter, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: TypeOfPath, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: Version, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: LBigDecimal, ctx: MethodCtx) = with(ctx) {
        val str = JcStringConstant(child.value, common.strType)
        genCtx.generateNewWithInit(common.names.getVarName(), common.bigDecimalType, listOf(str))
    }

    override fun visit(child: LBigInt, ctx: MethodCtx) = with(ctx) {
        val str = JcStringConstant(child.value, common.strType)
        genCtx.generateNewWithInit(common.names.getVarName(), common.bigIntType, listOf(str))
    }

    override fun visit(child: LBinary, ctx: MethodCtx) = with(ctx) {
        val bins = child.bytes.map { JcByte(it, cp.byte) }
        genCtx.putValuesWithSameTypeToArray(cp, common.names.getVarName(), bins, cp.byte)
    }

    override fun visit(child: LBool, ctx: MethodCtx) = with(ctx) {
        val v = newVar(common.boolType)
        val b = JcBool(child.value, common.boolType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, b) }
        v
    }

    override fun visit(child: LDouble, ctx: MethodCtx) = with(ctx) {
        val v = newVar(common.doubleType)
        val d = JcDouble(child.value, common.doubleType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, d) }
        v
    }

    override fun visit(child: LFloat, ctx: MethodCtx) = with(ctx) {
        val v = newVar(common.floatType)
        val d = JcFloat(child.value, common.floatType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, d) }
        v
    }

    override fun visit(child: LInt, ctx: MethodCtx) = with(ctx) {
        val v = JcInt(child.value, cp.int)
        genCtx.generateStaticCall(common.names.getVarName(), "valueOf", common.integerType, listOf(v))
    }

    override fun visit(child: LLong, ctx: MethodCtx) = with(ctx) {
        val v = newVar(common.longType)
        val d = JcLong(child.value, common.doubleType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, d) }
        v
    }

    override fun visit(child: LNull, ctx: MethodCtx) = with(ctx) {
        val v = newVar(cp.objectType)
        val n = JcNullConstant(cp.objectType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, n) }
        v
    }

    override fun visit(child: LString, ctx: MethodCtx) = with(ctx) {
        val v = newVar(common.strType)
        val str = JcStringConstant(child.value, common.strType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, str) }
        v
    }

    override fun visit(child: LTime, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: ExprPath, ctx: MethodCtx) = with(child) {
        val sPath = path.path
        if (path.isSimple())
            ctx.genObj(sPath.root, isGrouped)
        else
            ctx.genField(sPath.root, sPath.cont, isGrouped)
    }

    override fun visit(child: SyntacticPath, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: CaseList, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: SimpleCaseList, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: Parameter, ctx: MethodCtx) = child.genInst(ctx)

    override fun visit(child: Subquery, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: Tuple, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: Instance, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: SqlFunction, ctx: MethodCtx) = with(child) {
        if (func.isAggregator()) genAggregatorInst(ctx)
        else genSimpleFuncInst(ctx)
    }

    override fun visit(child: Positional, ctx: MethodCtx) = child.access(ctx, child.position(ctx.common))

    override fun visit(child: Colon, ctx: MethodCtx) = child.access(ctx, child.position(ctx.common))

    override fun visit(child: Compare, ctx: MethodCtx) = child.generateInst(ctx)

    override fun visit(child: ExistCollection, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: InFunction, ctx: MethodCtx) = child.generateInst(ctx)

    override fun visit(child: Between, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: Exist, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: IsDistinct, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: IsEmpty, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: IsNull, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: IsTrue, ctx: MethodCtx) = child.expr.genInst(ctx)

    override fun visit(child: Like, ctx: MethodCtx) = child.generateInst(ctx)

    override fun visit(child: Member, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: And, ctx: MethodCtx) = child.generateInst(ctx)

    override fun visit(child: Not, ctx: MethodCtx) = child.generateInst(ctx)

    override fun visit(child: Or, ctx: MethodCtx) = child.generateInst(ctx)

    override fun visit(child: ListIn, ctx: MethodCtx) = child.list.genInstAndWrapToList(ctx)

    override fun visit(child: ParameterIn, ctx: MethodCtx) = child.param.genInst(ctx)
}
