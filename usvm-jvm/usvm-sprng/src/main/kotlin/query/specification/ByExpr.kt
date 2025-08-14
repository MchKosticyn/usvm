package machine.interpreter.transformers.springjpa.query.specification

import machine.interpreter.transformers.springjpa.generateLambda
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expresion.Expression
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.jvm.util.toJcType

class ByExpr(val expr: Expression) : Specification() {

    override fun getLambdas(info: CommonInfo) = listOf(getTranslateMethod(info)) + expr.getLambdas(info)

    private var translateMethod: JcMethod? = null
    fun getTranslateMethod(info: CommonInfo): JcMethod {
        translateMethod?.also { return it }
        translateMethod = expr.toLambda(info)
        return translateMethod!!
    }

    override fun getTranslateRetType(ctx: MethodCtx) = with(ctx) {
        getTranslateMethod(common).returnType.toJcType(cp)!!
    }

    override fun getTranslate(ctx: MethodCtx): JcLocalVar {
        val method = getTranslateMethod(ctx.common)
        val lambda = ctx.genCtx.generateLambda(ctx.cp, "${ctx.getLambdaName()}_var", method)
        return lambda
    }

    override fun getComparer(ctx: MethodCtx): JcLocalVar = with(ctx) {
        val exprType = expr.type.getType(common) as JcClassType
        val method = common.utilsType.declaredMethods.single {
            it.name == common.comparerName && it.isStatic && it.parameters.size == 2
                    && it.parameters.all { p -> p.type == exprType }
        }.method

        return genCtx.generateLambda(cp, "${getLambdaName()}_var", method)
    }
}
