package machine.interpreter.transformers.springjpa.query.function

import machine.interpreter.transformers.springjpa.JAVA_OBJ_ARR
import machine.interpreter.transformers.springjpa.JcBodyFillerFeature
import machine.interpreter.transformers.springjpa.JcMethodBuilder
import machine.interpreter.transformers.springjpa.REPOSITORY_LAMBDA
import machine.interpreter.transformers.springjpa.generateLambda
import machine.interpreter.transformers.springjpa.generateNewWithInit
import machine.interpreter.transformers.springjpa.generateStaticCall
import machine.interpreter.transformers.springjpa.putValuesWithSameTypeToArray
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expresion.Expression
import machine.interpreter.transformers.springjpa.query.type.Primitive
import machine.interpreter.transformers.springjpa.query.type.SqlType
import machine.interpreter.transformers.springjpa.repositoryLambda
import machine.interpreter.transformers.springjpa.toArgument
import machine.interpreter.transformers.springjpa.toJavaClass
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.objectweb.asm.Opcodes
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext

open class SqlFunction(val func: Function, val args: List<Expression>): Expression() {

    override val type: SqlType = func.retType(args)

    override fun genInst(ctx: MethodCtx): JcLocalVar = with(ctx) {
        // Query is ITable<ITable> or ITable<Object[]> and we need to take 'this' table to aggregate
        val tblToAggr = method.parameters.get(if (isGrouped) 0 else 2).toArgument
        val aggrLambda = genCtx.generateLambda(cp, "aggr_lambda_${getLambdaName()}", getOwnMethod(common))
        val origMethodArgs = method.parameters.get(1).toArgument
        val args = listOf(tblToAggr, aggrLambda, origMethodArgs)
        val mapped = genCtx.generateNewWithInit("aggr_mapper_${getVarName()}", common.mapperType, args)

        val aggr_args = mutableListOf(mapped)
        if (!func.hasCommonRetType) {
            val typ = type.getType(common).let { genCtx.toJavaClass(cp, "aggr_${getVarName()}", it) }
            aggr_args.add(typ)
        }

        genCtx.generateStaticCall(
            "aggr_call_${getVarName()}",
            func.functionName,
            common.aggregatorsType,
            aggr_args
        )
    }

    override fun getLambdas(info: CommonInfo) = args.flatMap { it.getLambdas(info) } + getOwnMethod(info)

    // for ... SUM(book.id + 1) ... we need to use lambda book.id + 1 on table that we aggregate
    // so we need to add this lambda to repository interface
    private var cachedSelector: JcMethod? = null
    fun getOwnMethod(info: CommonInfo): JcMethod {
        cachedSelector?.also { return it }
        val methodName = info.names.getLambdaName()
        val method = JcMethodBuilder(info.repo)
            .setName(methodName)
            .setRetType(JAVA_OBJ_ARR)
            .setAccess(Opcodes.ACC_STATIC)
            .addBlanckAnnot(REPOSITORY_LAMBDA)
            .addFreshParam(JAVA_OBJ_ARR)
            .addFreshParam(JAVA_OBJ_ARR)
            .addFillerFuture(SqlFunctionInnerLambdaFeature(info, this, methodName))
            .buildMethod()
        cachedSelector = method
        return method
    }

    enum class Function(
        val functionName: String,
        val hasCommonRetType: Boolean,
        val retType: (List<Expression>) -> SqlType
    ) {
        // https://docs.jboss.org/hibernate/orm/6.4/querylanguage/html_single/Hibernate_Query_Language.html#aggregation
        COUNT("count", true, { _ -> Primitive.Long() }),
        AVG("avg", true, { _ -> Primitive.Double() }),
        MIN("min", false, { args -> args.single().type } ),
        MAX("max", false, { args -> args.single().type }),
        SUM("sum", false, { args -> args.single().type.upcastSumRetType() }),
        VAR_POP("varPop", true, { _ -> Primitive.Double() }),
        VAR_SAMP("varSamp", true, { _ -> Primitive.Double() }),
        STDDEV_POP("stddevPop", true, { _ -> Primitive.Double() }),
        STDDEV_SAMP("stddevSamp", true, { _ -> Primitive.Double() }),
        ANY("any", true, { _ -> Primitive.Bool() }),
        EVERY("every", true, { _ -> Primitive.Bool() })
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
}

private fun Primitive.isIntegral() =
    when (this) {
        is Primitive.Bool, is Primitive.Int, is Primitive.Long -> true
        else -> false
    }

private fun Primitive.isFloating() =
    when (this) {
        is Primitive.Float, is Primitive.Double -> true
        else -> false
    }

private fun SqlType.upcastSumRetType() =
    when (this) {
        is Primitive -> {
            if (isIntegral()) Primitive.Long()
            else if (isFloating()) Primitive.Float()
            else this
        }

        else -> this
    }

class InstCtx() {

}
