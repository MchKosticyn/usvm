package org.usvm.machine.interpreter.transformers.springjpa.query.selectfun

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.objectweb.asm.Opcodes
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import org.usvm.machine.interpreter.transformers.springjpa.JAVA_OBJ_ARR
import org.usvm.machine.interpreter.transformers.springjpa.JcBodyFillerFeature
import org.usvm.machine.interpreter.transformers.springjpa.JcMethodBuilder
import org.usvm.machine.interpreter.transformers.springjpa.REPOSITORY_LAMBDA
import org.usvm.machine.interpreter.transformers.springjpa.generateLambda
import org.usvm.machine.interpreter.transformers.springjpa.generateObjectArray
import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.repositoryLambda

class SelectFuntion(
    val isDistinct: Boolean,
    val selections: List<SelectionCtx>
) {

    abstract class SelectionCtx(var alias: String?) {
        abstract fun genInst(ctx: MethodCtx): JcLocalVar
    }

    fun collectAliases(): Map<String, SelectionCtx> {
        return selections.mapNotNull { s -> s.alias?.let { it to s } }
            .associate { p -> p }
    }

    fun getLambdas(info: CommonInfo) = listOf(getSelector(info))

    var cachedSelector: JcMethod? = null
    fun getSelector(info: CommonInfo): JcMethod {
        cachedSelector?.also { return it }
        val methodName = info.names.getLambdaName()
        val method = JcMethodBuilder(info.repo)
            .setName(methodName)
            .setRetType(info.origReturnGeneric)
            .setAccess(Opcodes.ACC_STATIC)
            .addBlanckAnnot(REPOSITORY_LAMBDA)
            .addFreshParam(JAVA_OBJ_ARR)
            .addFreshParam(JAVA_OBJ_ARR)
            .addFillerFuture(SelectFuture(info, this, methodName))
            .buildMethod()
        cachedSelector = method
        return method
    }

    fun getLambdaVar(ctx: MethodCtx): JcLocalVar {
        val method = getSelector(ctx.common)
        val lambda = ctx.genCtx.generateLambda(ctx.cp, "${ctx.getLambdaName()}Var", method)
        return lambda
    }

    class SelectFuture(val info: CommonInfo, val select: SelectFuntion, val name: String) : JcBodyFillerFeature() {

        override fun condition(method: JcMethod) = method.name == name && method.repositoryLambda

        override fun BlockGenerationContext.generateBody(method: JcMethod) {
            val ctx = MethodCtx(info.cp, info.query, info.repo, method, info.origMethod, this)

            val selVars = select.selections.map { it.genInst(ctx) }
            if (ctx.common.origReturnGeneric != JAVA_OBJ_ARR) {
                ctx.genCtx.addInstruction { loc -> JcReturnInst(loc, selVars.single()) }
            } else {
                val res = ctx.genCtx.generateObjectArray(ctx.cp, ctx.names.getVarName(), selVars.size)
                selVars.forEachIndexed { ix, s ->
                    val ass = JcArrayAccess(res, JcInt(ix, ctx.cp.int), ctx.cp.objectType)
                    ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, ass, s) }
                }

                ctx.genCtx.addInstruction { loc -> JcReturnInst(loc, res) }
            }
        }
    }
}
