package machine.interpreter.transformers.springjpa.query.selectfunction

import JcMethodBuilder
import jpa.DATA_ROW
import jpa.ITABLE
import jpa.JAVA_OBJ_ARR
import jpa.REPOSITORY_LAMBDA
import jpa.generateLambda
import jpa.generateNewWithInit
import jpa.putValuesWithSameTypeToArray
import jpa.repositoryLambda
import jpa.transformers.JcBodyFillerFeature
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.ext.objectType
import org.objectweb.asm.Opcodes
import org.usvm.jvm.util.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import org.usvm.spring.query.selectfun.ASelection
import org.usvm.spring.query.selectfun.SelectFunction

fun SelectFunction.bindGroupBy() {
    isGrouped = true
    selections.forEach(ASelection::bindGroupBy)
}

fun SelectFunction.collectAliases(): Map<String, ASelection> {
    return selections.mapNotNull { s -> s.alias?.let { it to s } }
        .associate { p -> p }
}

fun SelectFunction.getLambdas(info: CommonInfo) = selections.flatMap { it.getLambdas(info) } + getOwnMethod(info)

fun SelectFunction.getOwnMethod(info: CommonInfo): JcMethod {
    cachedSelector?.also { return it as JcMethod }
    val methodName = info.names.getLambdaName()
    val method = JcMethodBuilder(info.repo)
        .setName(methodName)
        .setRetType(info.origReturnGeneric)
        .setAccess(Opcodes.ACC_STATIC)
        .addBlancAnnot(REPOSITORY_LAMBDA)
        .addFreshParam(if (isGrouped) ITABLE else DATA_ROW) // Query is ITable<ITable> or ITable<DataRow>
        .addFreshParam(JAVA_OBJ_ARR) // top-level method's args
        .addFreshParam(ITABLE) // ref to current query for aggregators
        .addFillerFeature(SelectFuture(info, this, methodName))
        .buildMethod()
    cachedSelector = method
    return method
}

fun SelectFunction.getLambdaVar(ctx: MethodCtx) = with(ctx) {
    genCtx.generateLambda(cp, "selector_${getLambdaName()}", getOwnMethod(common))
}

fun SelectFunction.applySelect(tbl: JcLocalVar, ctx: MethodCtx) = with(ctx) {
    val selectFunction = getLambdaVar(ctx)

    val args = listOf(tbl, selectFunction, getMethodArgs())
    val mapped = genCtx.generateNewWithInit("select_mapped", common.mapperType, args)

    if (isDistinct)
        genCtx.generateNewWithInit("select_distinct", common.distinctType, listOf(mapped))
    else mapped
}

private class SelectFuture(val info: CommonInfo, val select: SelectFunction, val name: String) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = method.name == name && method.repositoryLambda

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val ctx = MethodCtx(info.cp, info.query, info.repo, method, info.origMethod, this)

        val selVars = select.selections.map { it.genInst(ctx) }
        val res = if (ctx.common.origReturnGeneric != JAVA_OBJ_ARR) {
            selVars.single()
        } else {
            ctx.genCtx.putValuesWithSameTypeToArray(ctx.cp, "select_result_obj_arr_wrap", selVars, ctx.cp.objectType)
        }

        ctx.genCtx.addInstruction { loc -> JcReturnInst(loc, res) }
    }
}
