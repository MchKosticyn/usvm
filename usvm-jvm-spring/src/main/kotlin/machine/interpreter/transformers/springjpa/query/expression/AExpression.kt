package machine.interpreter.transformers.springjpa.query.expression

import JcMethodBuilder
import jpa.DATA_ROW
import jpa.ITABLE
import jpa.JAVA_OBJ_ARR
import jpa.REPOSITORY_LAMBDA
import jpa.repositoryLambda
import jpa.transformers.JcBodyFillerFeature
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.type.getType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.objectweb.asm.Opcodes
import org.usvm.jvm.util.transformers.JcSingleInstructionTransformer
import org.usvm.spring.query.expression.AExpression

fun AExpression.toLambda(info: CommonInfo): JcMethod {
    cached?.also { return it as JcMethod }
    val methodName = info.names.getMethodName()
    val method = JcMethodBuilder(info.repo)
        .setName(methodName)
        .setRetType(type().getType(info).typeName)
        .setAccess(Opcodes.ACC_STATIC)
        .addBlanckAnnot(REPOSITORY_LAMBDA)
        .addFreshParam(if (isGrouped) ITABLE else DATA_ROW)
        .addFreshParam(JAVA_OBJ_ARR)
        .addFillerFuture(ToMethodFeature(info, this, methodName))
        .buildMethod()
    cached = method
    return method
}

fun AExpression.bindGroupBy() { isGrouped = true }

class ToMethodFeature(val info: CommonInfo, val expr: AExpression, val methodName: String) : JcBodyFillerFeature() {
    override fun condition(method: JcMethod) = method.repositoryLambda && method.name == methodName

    override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {
        val ctx = MethodCtx(info.cp, info.query, info.repo, method, info.origMethod, this)
        val expr = expr.genInst(ctx)
        addInstruction { loc -> JcReturnInst(loc, expr) }
    }
}
