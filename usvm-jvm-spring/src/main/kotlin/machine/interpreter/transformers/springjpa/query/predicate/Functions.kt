package machine.interpreter.transformers.springjpa.query.predicate

import machine.interpreter.transformers.springjpa.compare
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expresion.Expression
import machine.interpreter.transformers.springjpa.query.expresion.LNull
import machine.interpreter.transformers.springjpa.query.expresion.SingleArgumentExpression
import machine.interpreter.transformers.springjpa.query.path.Path
import machine.interpreter.transformers.springjpa.query.path.SimplePath
import machine.interpreter.transformers.springjpa.toBoolean
import machine.interpreter.transformers.springjpa.toInt
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcConditionExpr
import org.jacodb.api.jvm.cfg.JcEqExpr
import org.jacodb.api.jvm.cfg.JcGeExpr
import org.jacodb.api.jvm.cfg.JcGtExpr
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLeExpr
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcLtExpr
import org.jacodb.api.jvm.cfg.JcNeqExpr
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.int

class IsNull(expr: Expression): SinglePredicate(expr) {
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class IsEmpty(expr: Expression): SinglePredicate(expr) {
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class IsTrue(expr: Expression): SinglePredicate(expr) {
    override fun genInst(ctx: MethodCtx) = expr.genInst(ctx)
}

class IsDistinct(expr: Expression, val from: Expression): DoublePredicate(expr, from) {
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Member(expr: Expression, val of: Path): SinglePredicate(expr) {
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class In(expr: Expression, val list: ListCtx): SinglePredicate(expr) {
    class ListCtx()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Between(val expr: Expression, val left: Expression, val right: Expression): ManyPredicate(listOf(expr, left, right)) {
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Like(
    val expr: Expression,
    val pattern: Expression,
    val escape: Expression?,
    val caseSenc: Boolean
): ManyPredicate(listOfNotNull(expr, pattern, escape)) {
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        val expr = expr.genInst(ctx)
        val pattern = pattern.genInst(ctx)
        val esc = escape?.genInst(ctx) ?: LNull().genInst(ctx)
        val senc = JcBool(caseSenc, ctx.cp.boolean)
        val name = "${ctx.getVarName()}#like"
        return ctx.genStaticCall(name, "like", listOf(expr, pattern, esc, senc))
    }
}

class Compare(left: Expression, right: Expression, val operator: Operator): DoublePredicate(left, right) {
    enum class Operator {
        Equal, NotEqual, Greater, GreaterEqual, Less, LessEqual
    }

    private fun getMethodName(): String {
        return when (operator) {
            Operator.Equal -> "equals"
            Operator.NotEqual -> "notEquals"
            Operator.Greater -> "greater"
            Operator.GreaterEqual -> "greaterEq"
            Operator.Less -> "less"
            Operator.LessEqual -> "lessEq"
        }
    }

    // compare(l, r) [=<, <, ...] [0, 1, -1]
    private fun getCondition(ctx: MethodCtx): (JcLocalVar) -> JcConditionExpr {
        fun cond(condFun: (JcType, JcValue, JcValue) -> JcConditionExpr, i: Int): (JcLocalVar) -> JcConditionExpr {
            val type = ctx.cp.boolean
            val cmpv = JcInt(i, ctx.cp.int)

            return { v -> condFun(type, v, cmpv) }
        }

        return when (operator) {
            Operator.Equal -> cond(::JcEqExpr, 0)
            Operator.NotEqual -> cond(::JcNeqExpr, 0)
            Operator.Greater -> cond(::JcGtExpr, 0)
            Operator.GreaterEqual -> cond(::JcGeExpr, 0)
            Operator.Less -> cond(::JcLtExpr, 0)
            Operator.LessEqual -> cond(::JcLeExpr, 0)
        }
    }

    override fun genInst(ctx: MethodCtx): JcLocalVar = with(ctx) {
        val l = left.genInst(ctx)
        val r = right.genInst(ctx)

        val cmpRes = genStaticCall(getVarName(), common.comparerName, listOf(l, r))

        val downcasted = genCtx.toInt(cp, cmpRes)

        val cond = getCondition(ctx)
        val res = genCtx.compare(cp, cond(downcasted), getVarName())
        return genCtx.toBoolean(cp, res)
    }
}

class ExistCollection(val quantifier: ColQuantifierCtx, val path: SimplePath): NoArgsPredicate() {
    class ColQuantifierCtx()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Exist(expr: Expression): SinglePredicate(expr) {
    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
