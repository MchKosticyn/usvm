package machine.interpreter.transformers.springjpa.query.expresion

import machine.interpreter.transformers.springjpa.DATA_ROW
import machine.interpreter.transformers.springjpa.ITABLE
import machine.interpreter.transformers.springjpa.JAVA_OBJ_ARR
import machine.interpreter.transformers.springjpa.JcBodyFillerFeature
import machine.interpreter.transformers.springjpa.JcMethodBuilder
import machine.interpreter.transformers.springjpa.REPOSITORY_LAMBDA
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.Lambdable
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.type.SqlType
import machine.interpreter.transformers.springjpa.repositoryLambda
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.objectweb.asm.Opcodes
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer

abstract class Expression : Lambdable() {
    abstract val type: SqlType
    abstract fun genInst(ctx: MethodCtx): JcLocalVar

    var isGrouped = false

    fun bindGroupBy() {
        isGrouped = true
    }

    private var cached: JcMethod? = null
    fun toLambda(info: CommonInfo): JcMethod {
        cached?.also { return it }
        val methodName = info.names.getMethodName()
        val method = JcMethodBuilder(info.repo)
            .setName(methodName)
            .setRetType(type.getType(info).typeName)
            .setAccess(Opcodes.ACC_STATIC)
            .addBlanckAnnot(REPOSITORY_LAMBDA)
            .addFreshParam(if (isGrouped) ITABLE else DATA_ROW)
            .addFreshParam(JAVA_OBJ_ARR)
            .addFillerFuture(ToMethodFeature(info, this, methodName))
            .buildMethod()
        cached = method
        return method
    }
}

abstract class NoLambdaExpression : Expression() {
    override fun getLambdas(info: CommonInfo) = emptyList<JcMethod>()
}

abstract class SingleArgumentExpression(val expr: Expression) : Expression() {
    override fun getLambdas(info: CommonInfo) = expr.getLambdas(info)
}

abstract class DoubleArgumentExpression(val left: Expression, val right: Expression) : Expression() {
    override fun getLambdas(info: CommonInfo) = left.getLambdas(info) + right.getLambdas(info)
}

abstract class ManyArgumentExpression(val args: List<Expression>) : Expression() {
    override fun getLambdas(info: CommonInfo) = args.flatMap { it.getLambdas(info) }
}

class ToMethodFeature(val info: CommonInfo, val expr: Expression, val methodName: String) : JcBodyFillerFeature() {
    override fun condition(method: JcMethod) = method.repositoryLambda && method.name == methodName

    override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {
        val ctx = MethodCtx(info.cp, info.query, info.repo, method, info.origMethod, this)
        val expr = expr.genInst(ctx)
        addInstruction { loc -> JcReturnInst(loc, expr) }
    }
}
