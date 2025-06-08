package machine.interpreter.transformers.springjpa.query.predicate

import machine.interpreter.transformers.springjpa.compare
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expresion.DoubleArgumentExpression
import machine.interpreter.transformers.springjpa.query.expresion.Expression
import machine.interpreter.transformers.springjpa.query.expresion.ManyArgumentExpression
import machine.interpreter.transformers.springjpa.query.expresion.NoLambdaExpression
import machine.interpreter.transformers.springjpa.query.expresion.SingleArgumentExpression
import machine.interpreter.transformers.springjpa.query.type.Primitive
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcEqExpr
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcNeqExpr
import org.jacodb.api.jvm.ext.boolean

abstract class NoArgsPredicate(): NoLambdaExpression() {
    override val type = Primitive.Bool()
}

fun makeNot(not: Any?, expr: Expression) = not?.let { SinglePredicate.Not(expr) } ?: expr

abstract class SinglePredicate(val predicate: Expression): SingleArgumentExpression(predicate) {
    override val type = Primitive.Bool()

    class Not(predicate: Expression): SinglePredicate(predicate) {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
            val pr = predicate.genInst(ctx)
            val cond = JcNeqExpr(ctx.cp.boolean, pr, ctx.common.jcTrue)
            val ifRes = ctx.genCtx.compare(ctx.cp, cond, ctx.getPredicateName())
            return ifRes
        }
    }
}

abstract class DoublePredicate(left: Expression, right: Expression): DoubleArgumentExpression(left, right) {
    override val type = Primitive.Bool()

    class And(left: Expression, right: Expression): DoublePredicate(left, right) {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
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
    }

    class Or(left: Expression, right: Expression): DoublePredicate(left, right) {
        override fun genInst(ctx: MethodCtx): JcLocalVar {
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
    }
}

abstract class ManyPredicate(args: List<Expression>): ManyArgumentExpression(args) {
    override val type = Primitive.Bool()
}
