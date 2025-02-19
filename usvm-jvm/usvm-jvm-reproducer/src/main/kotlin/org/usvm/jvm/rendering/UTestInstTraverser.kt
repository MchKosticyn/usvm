package org.usvm.jvm.rendering

import org.usvm.test.api.UTestArithmeticExpression
import org.usvm.test.api.UTestArrayGetExpression
import org.usvm.test.api.UTestArrayLengthExpression
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestBinaryConditionExpression
import org.usvm.test.api.UTestBinaryConditionStatement
import org.usvm.test.api.UTestCastExpression
import org.usvm.test.api.UTestConstructorCall
import org.usvm.test.api.UTestCreateArrayExpression
import org.usvm.test.api.UTestGetFieldExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestSetFieldStatement
import org.usvm.test.api.UTestSetStaticFieldStatement
import org.usvm.test.api.UTestStaticMethodCall

object UTestInstTraverser {

    fun traverseInst(expr: UTestInst, depth: Int = 0, block: (UTestInst, Int) -> Unit): Unit = block(expr, depth).also {
        when (expr) {
            is UTestArithmeticExpression -> {
                traverseInst(expr.lhv, depth + 1, block)
                traverseInst(expr.rhv, depth + 1, block)
            }

            is UTestArrayGetExpression -> {
                traverseInst(expr.arrayInstance, depth + 1, block)
                traverseInst(expr.index, depth + 1, block)
            }

            is UTestArrayLengthExpression -> traverseInst(expr.arrayInstance, depth + 1, block)
            is UTestBinaryConditionExpression -> {
                traverseInst(expr.lhv, depth + 1, block)
                traverseInst(expr.rhv, depth + 1, block)
                traverseInst(expr.trueBranch, depth + 1, block)
                traverseInst(expr.elseBranch, depth + 1, block)
            }

            is UTestConstructorCall, is UTestStaticMethodCall -> {
                expr.args.forEach { arg -> traverseInst(arg, depth + 1, block) }
            }

            is UTestMethodCall -> {
                traverseInst(expr.instance, depth + 1, block)
                expr.args.forEach { arg -> traverseInst(arg, depth + 1, block) }
            }

            is UTestCastExpression -> traverseInst(expr.expr, depth + 1, block)
            is UTestCreateArrayExpression -> {
                traverseInst(expr.size, depth + 1, block)
            }

            is UTestGetFieldExpression -> {
                traverseInst(expr.instance, depth + 1, block)
            }

            is UTestArraySetStatement -> {
                traverseInst(expr.arrayInstance, depth + 1, block)
                traverseInst(expr.index, depth + 1, block)
                traverseInst(expr.setValueExpression, depth + 1, block)
            }

            is UTestBinaryConditionStatement -> {
                TODO()
            }

            is UTestSetFieldStatement -> {
                traverseInst(expr.instance, depth + 1, block)
                traverseInst(expr.value, depth + 1, block)

            }

            is UTestSetStaticFieldStatement -> {
                traverseInst(expr.value, depth + 1, block)
            }

            else -> return@also
        }
    }
}
