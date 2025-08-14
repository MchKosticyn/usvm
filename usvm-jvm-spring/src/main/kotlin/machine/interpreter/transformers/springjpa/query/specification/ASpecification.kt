package machine.interpreter.transformers.springjpa.query.specification

import jpa.generateLambda
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expression.getLambdas
import machine.interpreter.transformers.springjpa.query.expression.type
import machine.interpreter.transformers.springjpa.query.type.getType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.jvm.util.toJcType
import org.usvm.spring.query.specification.ASpecification
import org.usvm.spring.query.specification.ByExpression
import org.usvm.spring.query.specification.ByIdentity
import org.usvm.spring.query.specification.ByPosition
import org.usvm.spring.query.specification.ISpecificationVisitor

fun ASpecification.getLambdas(info: CommonInfo): List<JcMethod> = this.accept(specificationGetLambdasVisitor, info)
private val specificationGetLambdasVisitor = object : ISpecificationVisitor<List<JcMethod>, CommonInfo> {
    override fun visit(child: ByExpression, ctx: CommonInfo) = with(child) {
        expr.getLambdas(ctx) + getTranslateMethod(ctx)
    }

    override fun visit(child: ByIdentity, ctx: CommonInfo) = emptyList<JcMethod>()

    override fun visit(child: ByPosition, ctx: CommonInfo) = emptyList<JcMethod>()
}

fun ASpecification.getTranslate(ctx: MethodCtx): JcLocalVar = this.accept(specificationGetTranslateVisitor, ctx)
private val specificationGetTranslateVisitor = object : ISpecificationVisitor<JcLocalVar, MethodCtx> {
    override fun visit(child: ByExpression, ctx: MethodCtx): JcLocalVar {
        val method = child.getTranslateMethod(ctx.common)
        return ctx.genCtx.generateLambda(ctx.cp, "${ctx.getLambdaName()}_var", method)
    }

    override fun visit(child: ByIdentity, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: ByPosition, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

fun ASpecification.getTranslateRetType(ctx: MethodCtx): JcType =
    this.accept(specificationGetTranslateRetTypeVisitor, ctx)
private val specificationGetTranslateRetTypeVisitor = object : ISpecificationVisitor<JcType, MethodCtx> {
    override fun visit(child: ByExpression, ctx: MethodCtx) = with(ctx) {
        child.getTranslateMethod(ctx.common).returnType.toJcType(cp)!!
    }

    override fun visit(child: ByIdentity, ctx: MethodCtx): JcType {
        TODO("Not yet implemented")
    }

    override fun visit(child: ByPosition, ctx: MethodCtx): JcType {
        TODO("Not yet implemented")
    }
}

fun ASpecification.getComparer(ctx: MethodCtx): JcLocalVar = this.accept(specificationGetComparerVisitor, ctx)
private val specificationGetComparerVisitor = object : ISpecificationVisitor<JcLocalVar, MethodCtx> {
    override fun visit(child: ByExpression, ctx: MethodCtx) = with(ctx) {
        val exprType = child.expr.type().getType(common) as JcClassType
        val method = common.utilsType.declaredMethods.single {
            it.name == common.comparerName && it.isStatic && it.parameters.size == 2
                    && it.parameters.all { p -> p.type == exprType }
        }.method

        genCtx.generateLambda(cp, "${getLambdaName()}_var", method)
    }

    override fun visit(child: ByIdentity, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: ByPosition, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
