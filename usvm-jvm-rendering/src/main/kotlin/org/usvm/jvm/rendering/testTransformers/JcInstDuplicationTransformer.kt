package org.usvm.jvm.rendering.testTransformers

import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.exp
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestCall
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestStatement

class JcInstDuplicationTransformer: JcTestTransformer() {
    private val visited: MutableSet<UTestInst> = Collections.newSetFromMap(IdentityHashMap())

    override fun transform(test: UTest): UTest {
        val initInstList = test.initStatements.mapNotNull {
            transformInstProxy(it)
        }
        return UTest(initInstList, test.callMethodExpression)
    }

    private fun transformInstProxy(inst: UTestInst): UTestInst? = when(inst) {
        in visited -> null
        else -> super.transformInst(inst)
    }
    override fun transform(stmt: UTestStatement): UTestStatement? {
        visited.add(stmt)
        return super.transform(stmt)
    }

    override fun transform(expr: UTestExpression): UTestExpression? {
        visited.add(expr)
        return super.transform(expr)
    }
}