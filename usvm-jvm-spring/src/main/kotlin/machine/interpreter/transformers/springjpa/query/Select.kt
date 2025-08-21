package machine.interpreter.transformers.springjpa.query

import jpa.JAVA_LIST
import jpa.JAVA_SET
import jpa.LIST_WRAPPER
import jpa.generateNewWithInit
import jpa.generateStaticCall
import jpa.generateVirtualCall
import jpa.reloadJpaTerm
import jpa.toArgument
import kotlinx.collections.immutable.toPersistentList
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.usvm.jvm.util.name
import org.usvm.jvm.util.toJcType
import org.usvm.jvm.util.transformers.JcSingleInstructionTransformer
import org.usvm.spring.query.Select

fun Select.genInst(
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

fun Select.genInstAndWrapToList(ctx: MethodCtx) = with(ctx) {
    query.genInst(this)
        .let {
            orders.fold(it) { p, order ->
                order.applyOrder(p, this)
            }.let {
                genCtx.generateNewWithInit("wrapper_res", cp.findType(LIST_WRAPPER) as JcClassType, listOf(it))
            }
        }
}

private fun wrapResult(ctx: MethodCtx, method: JcMethod, ordered: JcLocalVar): JcLocalVar = with(ctx) {
    // simple wrapper
    getWrapperType(common, method.returnType)?.also {
        return genCtx.generateNewWithInit("wrapper_res", it, listOf(ordered))
    }

    // page
    getPage(ctx, method, ordered)?.also { return it }

    val list = genCtx.generateNewWithInit("list_for_first", common.listType, listOf(ordered))
    val first = genCtx.generateVirtualCall("final_first_call", "first", common.listType, list, emptyList())

    // if return type is optional return Optional.ofNullable(value)
    getOptional(ctx, method, first)?.also { return it }

    first
}

private fun getOptional(ctx: MethodCtx, method: JcMethod, value: JcLocalVar): JcLocalVar? = with(ctx) {
    val optionalType = common.optionalType
    if (method.returnType.typeName != common.optionalType.name) return null

    genCtx.generateStaticCall("optional_for_first", "ofNullable", optionalType, listOf(value))
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

fun Select.getLambdas(
    cp: JcClasspath,
    repo: JcClassOrInterface,
    method: JcMethod
): List<JcMethod> {
    val info = CommonInfo(cp, query, repo, method, method)
    val queryLambdas = query.getLambdas(info)
    val orderLambas = orders.flatMap { it.getLambdas(info) }
    return queryLambdas.toPersistentList().addAll(orderLambas)
}

fun Select.getLambdas(info: CommonInfo) = getLambdas(info.cp, info.repo, info.origMethod)
