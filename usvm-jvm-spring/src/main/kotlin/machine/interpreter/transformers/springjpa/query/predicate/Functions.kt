package machine.interpreter.transformers.springjpa.query.predicate

import machine.interpreter.transformers.springjpa.compare
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expresion.Expression
import machine.interpreter.transformers.springjpa.query.expresion.LNull
import machine.interpreter.transformers.springjpa.query.path.Path
import machine.interpreter.transformers.springjpa.query.path.SimplePath
import machine.interpreter.transformers.springjpa.toBoolean
import machine.interpreter.transformers.springjpa.toInt
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

abstract class Function : PredicateCtx() {

    class IsNull(val expression: Expression) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class IsEmpty(val expression: Expression) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class IsTrue(val expression: Expression) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            return expression.genInst(ctx)
        }
    }

    class IsDistinct(val expression: Expression, val from: Expression) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Member(val expression: Expression, val of: Path) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class In(val expression: Expression, val list: ListCtx) : Function() {
        class ListCtx()

        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Between(val expression: Expression, val left: Expression, val right: Expression) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Like(
        val expression: Expression,
        val pattern: Expression,
        val escape: Expression?,
        val caseSenc: Boolean
    ) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            val expr = expression.genInst(ctx)
            val pattern = pattern.genInst(ctx)
            val esc = escape?.genInst(ctx) ?: LNull().genInst(ctx)
            val senc = JcBool(caseSenc, ctx.cp.boolean)
            val name = "${ctx.getVarName()}#like"
            return ctx.genStaticCall(name, "like", listOf(expr, pattern, esc, senc))
        }
    }

    class Compare(val left: Expression, val right: Expression, val operator: Operator) : Function() {
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

    class ExistCollection(val quantifier: ColQuantifierCtx, val path: SimplePath) : Function() {
        class ColQuantifierCtx()

        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class Exist(val expression: Expression) : Function() {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            TODO("Not yet implemented")
        }
    }
}
