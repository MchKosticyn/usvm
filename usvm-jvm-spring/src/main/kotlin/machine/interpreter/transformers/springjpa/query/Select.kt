package org.usvm.machine.interpreter.transformers.springjpa

import kotlinx.collections.immutable.toPersistentList
import machine.interpreter.transformers.springjpa.JAVA_LIST
import machine.interpreter.transformers.springjpa.JAVA_SET
import machine.interpreter.transformers.springjpa.generateNewWithInit
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.Lambdable
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.Order
import machine.interpreter.transformers.springjpa.query.Query
import machine.interpreter.transformers.springjpa.toArgument
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.int
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.usvm.jvm.util.name
import org.usvm.jvm.util.toJcType
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer

class Select(
    val orders: List<Order>,
    val query: Query
): Lambdable() {
    fun addOrder(order: List<Order>) = orders.toMutableList().addAll(order)

    fun genInst(
        cp: JcClasspath,
        repo: JcClassOrInterface,
        method: JcMethod,
        genCtx: JcSingleInstructionTransformer.BlockGenerationContext
    ): JcLocalVar {
        val ctx = MethodCtx(cp, query, repo, method, method, genCtx)

        val queryVar = query.genInst(ctx).let {
            orders.fold(it) { p, order ->
                order.applyOrder(p, ctx)
            }
        }
        val wrapped = wrapResult(ctx, method, queryVar)

        return wrapped
    }

    private fun wrapResult(ctx: MethodCtx, method: JcMethod, ordered: JcLocalVar): JcLocalVar {
        // simple wrapper
        getWrapperType(ctx.common, method.returnType)?.also {
            return ctx.genCtx.generateNewWithInit("wrapper_res", it, listOf(ordered))
        }

        // page
        getPage(ctx, method, ordered)?.also { return it }

        val list = ctx.genCtx.generateNewWithInit("list_for_first", ctx.common.listType, listOf(ordered))
        val first = ctx.newVar(method.returnType.toJcType(ctx.cp)!!)
        val firstF = ctx.common.listType.declaredMethods.single { it.name == "first" }
            .let { VirtualMethodRefImpl.of(ctx.common.listType, it) }
        val firstCall = JcVirtualCallExpr(firstF, list, listOf())
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, first, firstCall) }

        return first
    }

    private fun getPage(ctx: MethodCtx, method: JcMethod, ordered: JcLocalVar): JcLocalVar? {
        if (method.returnType.typeName != ctx.common.pageType.name) return null

        val listWrap = ctx.genCtx.generateNewWithInit("fst_wrapper", ctx.common.listType, listOf(ordered))
        val pagable = method.parameters.single {
            it.type.typeName == "org.springframework.data.domain.Pageable"
        }.toArgument

        val size = ctx.newVar(ctx.cp.int)
        val sizeF = ctx.common.listType.declaredMethods.single { it.name == "size" }
            .let { VirtualMethodRefImpl.of(ctx.common.listType, it) }
        val sizeCall = JcVirtualCallExpr(sizeF, listWrap, listOf())
        ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, size, sizeCall) }

        val args = listOf(listWrap, pagable, size)
        return ctx.genCtx.generateNewWithInit("page", ctx.common.pageImplType, args)
    }

    private fun getWrapperType(info: CommonInfo, type: TypeName): JcClassType? {
        return when (type.typeName) {
            JAVA_SET -> info.setType
            JAVA_LIST -> info.listType
            // TODO: more collections
            else -> null
        }
    }

    fun getLambdas(
        cp: JcClasspath,
        repo: JcClassOrInterface,
        method: JcMethod,
    ): List<JcMethod> {
        val info = CommonInfo(cp, query, repo, method, method)
        val queryLambdas = query.getLambdas(info)
        val orderLambas = orders.flatMap { it.getLambdas(info) }
        return queryLambdas.toPersistentList().addAll(orderLambas)
    }

    override fun getLambdas(info: CommonInfo) = getLambdas(info.cp, info.repo, info.origMethod)
}
