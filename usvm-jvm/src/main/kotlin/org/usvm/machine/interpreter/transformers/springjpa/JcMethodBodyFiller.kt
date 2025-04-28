package org.usvm.machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.impl.cfg.JcInstListImpl
import org.jacodb.impl.cfg.JcInstLocationImpl
import org.jacodb.impl.features.classpaths.AbstractJcInstResult
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext

abstract class JcBodyFillerFeature : JcMethodExtFeature {

    abstract fun condition(method: JcMethod): Boolean
    abstract fun BlockGenerationContext.generateBody(method: JcMethod)

    override fun instList(method: JcMethod): JcMethodExtFeature.JcInstListResult? {

        if (!condition(method)) return null

        val ctx = BlockGenerationContext(
            mutableListOf(),
            JcInstLocationImpl(method, 0, 0),
            0
        )

        ctx.generateBody(method)

        return AbstractJcInstResult.JcInstListResultImpl(method, JcInstListImpl(ctx.mutableInstructions.toList()))
    }
}
