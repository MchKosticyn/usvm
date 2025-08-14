package machine.interpreter.transformers.springjpa.query.function

import jpa.compare
import jpa.generateVirtualCall
import jpa.toBoolean
import jpa.toInt
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expression.genInst
import machine.interpreter.transformers.springjpa.query.expression.type
import machine.interpreter.transformers.springjpa.query.type.getType
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcConditionExpr
import org.jacodb.api.jvm.cfg.JcEqExpr
import org.jacodb.api.jvm.cfg.JcGeExpr
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcGtExpr
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLeExpr
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcLtExpr
import org.jacodb.api.jvm.cfg.JcNeqExpr
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.int
import org.usvm.spring.query.expression.literals.LNull
import org.usvm.spring.query.predicate.And
import org.usvm.spring.query.predicate.Not
import org.usvm.spring.query.predicate.Or
import org.usvm.spring.query.predicate.functions.InFunction.InFunction
import org.usvm.spring.query.predicate.functions.Like
import org.usvm.spring.query.predicate.functions.compare.Compare
import org.usvm.spring.query.predicate.functions.compare.Operator.EQUAL
import org.usvm.spring.query.predicate.functions.compare.Operator.GREATER
import org.usvm.spring.query.predicate.functions.compare.Operator.GREATER_EQUAL
import org.usvm.spring.query.predicate.functions.compare.Operator.LESS
import org.usvm.spring.query.predicate.functions.compare.Operator.LESS_EQUAL
import org.usvm.spring.query.predicate.functions.compare.Operator.NOT_EQUAL

// compare(l, r) [=<, <, ...] [0, 1, -1]
private fun Compare.getCondition(ctx: MethodCtx): (JcLocalVar) -> JcConditionExpr {
    fun cond(condFun: (JcType, JcValue, JcValue) -> JcConditionExpr, i: Int): (JcLocalVar) -> JcConditionExpr {
        val type = ctx.cp.boolean
        val cmpv = JcInt(i, ctx.cp.int)

        return { v -> condFun(type, v, cmpv) }
    }

    return when (operator) {
        EQUAL -> cond(::JcEqExpr, 0)
        NOT_EQUAL -> cond(::JcNeqExpr, 0)
        GREATER -> cond(::JcGtExpr, 0)
        GREATER_EQUAL -> cond(::JcGeExpr, 0)
        LESS -> cond(::JcLtExpr, 0)
        LESS_EQUAL -> cond(::JcLeExpr, 0)
    }
}

fun Compare.generateInst(ctx: MethodCtx) = with(ctx) {
    val l = left.genInst(ctx)
    val r = right.genInst(ctx)

    val cmpRes = genStaticCall(getVarName(), common.comparerName, listOf(l, r))

    val downcasted = genCtx.toInt(cp, cmpRes)

    val cond = getCondition(ctx)
    val res = genCtx.compare(cp, cond(downcasted), getVarName())
    genCtx.toBoolean(cp, res)
}

fun InFunction.generateInst(ctx: MethodCtx) = with(ctx) {
    val value = expr.genInst(ctx)
    val values = list.genInst(ctx)
    val isContains =
        genCtx.generateVirtualCall(
            "is_in",
            "contains",
            list.type().getType(common) as JcClassType,
            values,
            listOf(value)
        )
    genCtx.toBoolean(cp, isContains)
}

fun Like.generateInst(ctx: MethodCtx): JcLocalVar {
    val expr = expr.genInst(ctx)
    val pattern = pattern.genInst(ctx)
    val esc = escape?.genInst(ctx) ?: LNull().genInst(ctx)
    val senc = JcBool(caseSenc, ctx.cp.boolean)
    val name = "${ctx.getVarName()}#like"
    return ctx.genStaticCall(name, "like", listOf(expr, pattern, esc, senc))
}

fun Not.generateInst(ctx: MethodCtx): JcLocalVar {
    val pr = predicate.genInst(ctx)
    val cond = JcNeqExpr(ctx.cp.boolean, pr, ctx.common.jcTrue)
    val ifRes = ctx.genCtx.compare(ctx.cp, cond, ctx.getPredicateName())
    return ifRes
}

fun And.generateInst(ctx: MethodCtx): JcLocalVar {
    val genCtx = ctx.genCtx
    val l = left.genInst(ctx)
    val r = right.genInst(ctx)

    // 0. if l == false (jmp 4) (next)
    // 1. if r == false (jmp 4) (next)
    // 2. %0 = true
    // 3. goto 6
    // 4. %0 = false
    // 5. goto 6
    // 6. return %0
    val falseRes: JcInstRef
    val endOfIf: JcInstRef
    genCtx.addInstruction { loc ->
        val cond = JcEqExpr(ctx.cp.boolean, l, ctx.common.jcFalse)
        falseRes = JcInstRef(loc.index + 4)
        val nextInst = JcInstRef(loc.index + 1)
        endOfIf = JcInstRef(loc.index + 6)
        JcIfInst(loc, cond, falseRes, nextInst)
    }
    genCtx.addInstruction { loc ->
        val cond = JcEqExpr(ctx.cp.boolean, r, ctx.common.jcFalse)
        val nextInst = JcInstRef(loc.index + 1)
        JcIfInst(loc, cond, falseRes, nextInst)
    }

    val resVal = genCtx.nextLocalVar(ctx.getPredicateName(), ctx.cp.boolean)
    genCtx.addInstruction { loc -> JcAssignInst(loc, resVal, ctx.common.jcTrue) }
    genCtx.addInstruction { loc -> JcGotoInst(loc, endOfIf) }
    genCtx.addInstruction { loc -> JcAssignInst(loc, resVal, ctx.common.jcFalse) }
    genCtx.addInstruction { loc -> JcGotoInst(loc, endOfIf) }

    return resVal
}

fun Or.generateInst(ctx: MethodCtx): JcLocalVar {
    val genCtx = ctx.genCtx
    val l = left.genInst(ctx)
    val r = right.genInst(ctx)

    // 0. if l == true (jmp 2) (next)
    // 1. if r == false (jmp 4) (next)
    // 2. %0 = true
    // 3. goto 6
    // 4. %0 = false
    // 5. goto 6
    // 6. return %0
    val endOfIf: JcInstRef
    genCtx.addInstruction { loc ->
        val cond = JcEqExpr(ctx.cp.boolean, l, ctx.common.jcTrue)
        val trueBranch = JcInstRef(loc.index + 2)
        val nextInst = JcInstRef(loc.index + 1)
        endOfIf = JcInstRef(loc.index + 6)
        JcIfInst(loc, cond, trueBranch, nextInst)
    }
    genCtx.addInstruction { loc ->
        val cond = JcEqExpr(ctx.cp.boolean, r, ctx.common.jcFalse)
        val trueBranch = JcInstRef(loc.index + 3)
        val nextInst = JcInstRef(loc.index + 1)
        JcIfInst(loc, cond, trueBranch, nextInst)
    }

    val resVal = genCtx.nextLocalVar(ctx.getPredicateName(), ctx.cp.boolean)
    genCtx.addInstruction { loc -> JcAssignInst(loc, resVal, ctx.common.jcTrue) }
    genCtx.addInstruction { loc -> JcGotoInst(loc, endOfIf) }
    genCtx.addInstruction { loc -> JcAssignInst(loc, resVal, ctx.common.jcFalse) }
    genCtx.addInstruction { loc -> JcGotoInst(loc, endOfIf) }

    return resVal
}
