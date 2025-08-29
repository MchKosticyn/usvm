package machine.interpreter.transformers.springjpa.query.function

import JcMethodBuilder
import jpa.DATA_ROW
import jpa.JAVA_OBJ_ARR
import jpa.REPOSITORY_LAMBDA
import jpa.generateLambda
import jpa.generateNewWithInit
import jpa.generateStaticCall
import jpa.putValuesWithSameTypeToArray
import jpa.repositoryLambda
import jpa.toArgument
import jpa.toJavaClass
import jpa.transformers.JcBodyFillerFeature
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expression.genInst
import machine.interpreter.transformers.springjpa.query.expression.type
import machine.interpreter.transformers.springjpa.query.type.getType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.objectweb.asm.Opcodes
import org.usvm.jvm.util.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import org.usvm.spring.query.expression.AExpression
import org.usvm.spring.query.function.FunctionType
import org.usvm.spring.query.function.FunctionType.ANY
import org.usvm.spring.query.function.FunctionType.AVG
import org.usvm.spring.query.function.FunctionType.COUNT
import org.usvm.spring.query.function.FunctionType.DATE
import org.usvm.spring.query.function.FunctionType.EVERY
import org.usvm.spring.query.function.FunctionType.MAX
import org.usvm.spring.query.function.FunctionType.MIN
import org.usvm.spring.query.function.FunctionType.STDDEV_POP
import org.usvm.spring.query.function.FunctionType.STDDEV_SAMP
import org.usvm.spring.query.function.FunctionType.SUM
import org.usvm.spring.query.function.FunctionType.VAR_POP
import org.usvm.spring.query.function.FunctionType.VAR_SAMP
import org.usvm.spring.query.function.SqlFunction
import org.usvm.spring.query.type.AType
import org.usvm.spring.query.type.primitive.TBool
import org.usvm.spring.query.type.primitive.TDouble
import org.usvm.spring.query.type.primitive.TFloat
import org.usvm.spring.query.type.primitive.TInt
import org.usvm.spring.query.type.primitive.TLocalDate
import org.usvm.spring.query.type.primitive.TLong

fun SqlFunction.genAggregatorInst(ctx: MethodCtx) = with(ctx) {
    // Query is ITable<ITable> or ITable<DataRow> and we need to take 'this' table to aggregate
    val tblToAggr = method.parameters.get(if (isGrouped) 0 else 2).toArgument
    val aggrLambda = genCtx.generateLambda(cp, "aggr_lambda_${getLambdaName()}", getOwnMethod(common))
    val origMethodArgs = method.parameters.get(1).toArgument
    val args = listOf(tblToAggr, aggrLambda, origMethodArgs)

    val varName = getVarName()
    val mapped = genCtx.generateNewWithInit("aggr_mapper_$varName", common.mapperType, args)

    val distinct = if (isDistinct)
        genCtx.generateNewWithInit("aggr_distinct_$varName", common.distinctType, listOf(mapped))
    else mapped

    val aggrArgs = mutableListOf(distinct)
    if (!func.hasCommonRetType) {
        val typ = type().getType(common).let { genCtx.toJavaClass(cp, "aggr_$varName", it) }
        aggrArgs.add(typ)
    }

    genCtx.generateStaticCall(
        "aggr_call_$varName",
        func.name,
        common.aggregatorsType,
        aggrArgs
    )
}

fun SqlFunction.genSimpleFuncInst(ctx: MethodCtx) = with(ctx) {
    val arg = method.parameters.first().toArgument
    genCtx.generateStaticCall(
        "func_call_${getVarName()}",
        func.name,
        common.functionsType,
        listOf(arg)
    )
}

fun SqlFunction.getType() = func.getType(args)

fun FunctionType.isAggregator() = isAggregator

fun FunctionType.getType(args: List<AExpression>) = when(this) {
    COUNT -> TLong()
    AVG -> TDouble()
    MIN -> args.single().type()
    MAX -> args.single().type()
    SUM -> args.single().type().upcastSumRetType()
    VAR_POP -> TDouble()
    VAR_SAMP -> TDouble()
    STDDEV_POP -> TDouble()
    STDDEV_SAMP -> TDouble()
    ANY -> TBool()
    EVERY -> TBool()

    DATE -> TLocalDate()
}

private fun AType.isIntegral() =
    when (this) {
        is TBool, is TInt, is TLong -> true
        else -> false
    }

private fun AType.isFloating() =
    when (this) {
        is TFloat, is TDouble -> true
        else -> false
    }

private fun AType.upcastSumRetType() =
    if (isIntegral()) TLong()
    else if (isFloating()) TFloat()
    else this


fun SqlFunction.getOwnMethod(info: CommonInfo): JcMethod {
    cachedSelector?.also { return it as JcMethod }
    val methodName = info.names.getLambdaName()
    val method = JcMethodBuilder(info.repo)
        .setName(methodName)
        .setRetType(DATA_ROW)
        .setAccess(Opcodes.ACC_STATIC)
        .addBlancAnnot(REPOSITORY_LAMBDA)
        .addFreshParam(DATA_ROW)
        .addFreshParam(JAVA_OBJ_ARR)
        .addFillerFuture(SqlFunctionInnerLambdaFeature(info, this, methodName))
        .buildMethod()
    cachedSelector = method
    return method
}

class SqlFunctionInnerLambdaFeature(
    val info: CommonInfo,
    val func: SqlFunction,
    val name: String
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = method.name == name && method.repositoryLambda

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val ctx = MethodCtx(info.cp, info.query, info.repo, method, info.origMethod, this)
        val res = if (func.args.isEmpty()) ctx.method.parameters.first().toArgument
        else {
            val args = func.args.map { it.genInst(ctx) }
            ctx.genCtx.putValuesWithSameTypeToArray(ctx.cp, "aggregator_lambda_put", args)
        }
        addInstruction { loc -> JcReturnInst(loc, res) }
    }
}
