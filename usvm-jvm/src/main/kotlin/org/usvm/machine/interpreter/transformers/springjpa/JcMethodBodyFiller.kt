package org.usvm.machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcMethodExtFeature
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.impl.cfg.JcInstListImpl
import org.jacodb.impl.cfg.JcInstLocationImpl
import org.jacodb.impl.features.classpaths.AbstractJcInstResult
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext

class JcMethodBodyFiller(
    val method: JcMethod
) {

    val template = JcReturnInst(JcInstLocationImpl(method, 0, 0), null)
    val transformer = JcSingleInstructionTransformer(JcInstListImpl(listOf(template)))

    fun buildBody(): JcMethodExtFeature.JcInstListResult {
        val inst = transformer.buildInstList().toMutableList()

        // TODO: without single instruction transformer
        val last = inst.lastOrNull()
        if (last is JcGotoInst) {
            inst.remove(last)
        }

        return AbstractJcInstResult.JcInstListResultImpl(method, JcInstListImpl(inst.toList()))
    }

    fun generateReplacementBlock(blockGen: BlockGenerationContext.() -> Unit) {
        transformer.generateReplacementBlock(template) { blockGen() }
    }
}

abstract class JcBodyFillerFeature : JcMethodExtFeature {

    abstract fun condition(method: JcMethod): Boolean
    abstract fun BlockGenerationContext.generateBody(method: JcMethod)

    override fun instList(method: JcMethod): JcMethodExtFeature.JcInstListResult? {

        if (!condition(method)) return null

        val filler = JcMethodBodyFiller(method)
        filler.generateReplacementBlock { generateBody(method) }
        return filler.buildBody()
    }
}
