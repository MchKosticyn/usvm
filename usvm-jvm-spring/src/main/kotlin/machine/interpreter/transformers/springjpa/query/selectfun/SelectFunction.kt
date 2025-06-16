package machine.interpreter.transformers.springjpa.query.selectfun

import kotlinx.collections.immutable.persistentListOf
import machine.interpreter.transformers.springjpa.ITABLE
import machine.interpreter.transformers.springjpa.JAVA_OBJ_ARR
import machine.interpreter.transformers.springjpa.JcBodyFillerFeature
import machine.interpreter.transformers.springjpa.JcMethodBuilder
import machine.interpreter.transformers.springjpa.REPOSITORY_LAMBDA
import machine.interpreter.transformers.springjpa.generateLambda
import machine.interpreter.transformers.springjpa.generateNewWithInit
import machine.interpreter.transformers.springjpa.putValuesWithSameTypeToArray
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.Lambdable
import machine.interpreter.transformers.springjpa.query.ManyWithOwnLambdable
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.repositoryLambda
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.objectweb.asm.Opcodes
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext

class SelectFunction(
    val isDistinct: Boolean,
    val selections: List<SelectionCtx>
): ManyWithOwnLambdable(selections) {

    var isGrouped: Boolean = false

    fun bindGroupBy() {
        isGrouped = true
        selections.forEach(SelectionCtx::bindGroupBy)
    }

    abstract class SelectionCtx(var alias: String?): Lambdable() {
        abstract fun genInst(ctx: MethodCtx): JcLocalVar
        abstract fun bindGroupBy()
    }

    fun collectAliases(): Map<String, SelectionCtx> {
        return selections.mapNotNull { s -> s.alias?.let { it to s } }
            .associate { p -> p }
    }

    private var cachedSelector: JcMethod? = null
    override fun getOwnMethod(info: CommonInfo): JcMethod {
        cachedSelector?.also { return it }
        val methodName = info.names.getLambdaName()
        val method = JcMethodBuilder(info.repo)
            .setName(methodName)
            .setRetType(info.origReturnGeneric)
            .setAccess(Opcodes.ACC_STATIC)
            .addBlanckAnnot(REPOSITORY_LAMBDA)
            .addFreshParam(if (isGrouped) ITABLE else JAVA_OBJ_ARR) // Query is ITable<ITable> or ITable<Object[]>
            .addFreshParam(JAVA_OBJ_ARR) // top-level method's args
            .addFreshParam(ITABLE) // ref to current query for aggregators
            .addFillerFuture(SelectFuture(info, this, methodName))
            .buildMethod()
        cachedSelector = method
        return method
    }

    fun getLambdaVar(ctx: MethodCtx)= with(ctx) {
        genCtx.generateLambda(cp, "selector_${getLambdaName()}", getOwnMethod(common))
    }

    fun applySelect(tbl: JcLocalVar, ctx: MethodCtx) = with(ctx) {
        val selectFunction = getLambdaVar(ctx)

        val args = listOf(tbl, selectFunction, getMethodArgs())
        val mapped = genCtx.generateNewWithInit("select_mapped", common.mapperType, args)

        if (isDistinct)
            genCtx.generateNewWithInit("select_distinct", common.distinctType, listOf(mapped))
        else mapped
    }

    class SelectFuture(val info: CommonInfo, val select: SelectFunction, val name: String) : JcBodyFillerFeature() {

        override fun condition(method: JcMethod) = method.name == name && method.repositoryLambda

        override fun BlockGenerationContext.generateBody(method: JcMethod) {
            val ctx = MethodCtx(info.cp, info.query, info.repo, method, info.origMethod, this)

            val selVars = select.selections.map { it.genInst(ctx) }
            val res = if (ctx.common.origReturnGeneric != JAVA_OBJ_ARR) {
                selVars.single()
            } else {
                ctx.genCtx.putValuesWithSameTypeToArray(ctx.cp, "select_result_obj_arr_wrap", selVars)
            }

            ctx.genCtx.addInstruction { loc -> JcReturnInst(loc, res) }
        }
    }
}
