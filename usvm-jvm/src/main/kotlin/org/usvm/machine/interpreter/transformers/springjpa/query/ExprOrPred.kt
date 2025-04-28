package org.usvm.machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.objectweb.asm.Opcodes
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer
import org.usvm.machine.interpreter.transformers.springjpa.JAVA_OBJ_ARR
import org.usvm.machine.interpreter.transformers.springjpa.JcBodyFillerFeature
import org.usvm.machine.interpreter.transformers.springjpa.JcMethodBuilder
import org.usvm.machine.interpreter.transformers.springjpa.REPOSITORY_LAMBDA
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Type
import org.usvm.machine.interpreter.transformers.springjpa.repositoryLambda

abstract class ExprOrPred {
    abstract val type: Type
    abstract fun genInst(ctx: MethodCtx): JcLocalVar

    private var cached: JcMethod? = null
    fun toLambda(info: CommonInfo): JcMethod {
        cached?.also { return it }
        val methodName = info.names.getMethodName()
        val method = JcMethodBuilder(info.repo)
            .setName(methodName)
            .setRetType(type.getType(info).typeName)
            .setAccess(Opcodes.ACC_STATIC)
            .addBlanckAnnot(REPOSITORY_LAMBDA)
            .addFreshParam(JAVA_OBJ_ARR)
            .addFreshParam(JAVA_OBJ_ARR)
            .addFillerFuture(ToMethodFeature(info, this, methodName))
            .buildMethod()
        cached = method
        return method
    }
}

class ToMethodFeature(val info: CommonInfo, val expr: ExprOrPred, val methodName: String) : JcBodyFillerFeature() {
    override fun condition(method: JcMethod) = method.repositoryLambda && method.name == methodName

    override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {
        val ctx = MethodCtx(info.cp, info.query, info.repo, method, info.origMethod, this)
        val expr = expr.genInst(ctx)
        addInstruction { loc -> JcReturnInst(loc, expr) }
    }
}
