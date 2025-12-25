package org.usvm.jvm.rendering.testTransformers

import org.usvm.test.api.UTestCall
import org.usvm.test.api.UTestConstructorCall
import org.usvm.test.api.UTestMethodCall

class JcCallCtorTransformer: JcTestTransformer() {

    override fun transform(call: UTestMethodCall): UTestCall? {
        if (!call.method.isConstructor)
            return super.transform(call)

        val args = call.args.map { transformExpr(it) ?: return null }
        return UTestConstructorCall(call.method, args)
    }
}
