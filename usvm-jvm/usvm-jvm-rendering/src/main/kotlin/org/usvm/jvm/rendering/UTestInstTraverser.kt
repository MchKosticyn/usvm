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

    fun traverseInst(inst: UTestInst, depth: Int = 0, block: (UTestInst, Int) -> Unit): Unit = block(inst, depth).also {
        when (inst) {
//            is UTestAssertConditionExpression -> {
//                traverseInst(inst.args.single(), depth + 1, block)
//            }
//
//            is UTestAssertThrowsExpression -> {
//                inst.args.forEach { arg -> traverseInst(arg, depth + 1, block) }
//            }

            is UTestArithmeticExpression -> {
                traverseInst(inst.lhv, depth + 1, block)
                traverseInst(inst.rhv, depth + 1, block)
            }

            is UTestArrayGetExpression -> {
                traverseInst(inst.arrayInstance, depth + 1, block)
                traverseInst(inst.index, depth + 1, block)
            }

            is UTestArrayLengthExpression -> traverseInst(inst.arrayInstance, depth + 1, block)
            is UTestBinaryConditionExpression -> {
                traverseInst(inst.lhv, depth + 1, block)
                traverseInst(inst.rhv, depth + 1, block)
                traverseInst(inst.trueBranch, depth + 1, block)
                traverseInst(inst.elseBranch, depth + 1, block)
            }

            is UTestConstructorCall, is UTestStaticMethodCall -> {
                inst.args.forEach { arg -> traverseInst(arg, depth + 1, block) }
            }

            is UTestMethodCall -> {
                traverseInst(inst.instance, depth + 1, block)
                inst.args.forEach { arg -> traverseInst(arg, depth + 1, block) }
            }

            is UTestCastExpression -> traverseInst(inst.expr, depth + 1, block)
            is UTestCreateArrayExpression -> {
                traverseInst(inst.size, depth + 1, block)
            }

            is UTestGetFieldExpression -> {
                traverseInst(inst.instance, depth + 1, block)
            }

            is UTestArraySetStatement -> {
                traverseInst(inst.arrayInstance, depth + 1, block)
                traverseInst(inst.index, depth + 1, block)
                traverseInst(inst.setValueExpression, depth + 1, block)
            }

            is UTestBinaryConditionStatement -> {
                TODO()
            }

            is UTestSetFieldStatement -> {
                traverseInst(inst.instance, depth + 1, block)
                traverseInst(inst.value, depth + 1, block)

            }

            is UTestSetStaticFieldStatement -> {
                traverseInst(inst.value, depth + 1, block)
            }

            else -> return@also
        }
    }
}