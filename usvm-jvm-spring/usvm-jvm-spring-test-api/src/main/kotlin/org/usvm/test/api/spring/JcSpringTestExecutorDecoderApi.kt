package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClasspath
import org.usvm.test.api.JcTestExecutorDecoderApi
import org.usvm.test.api.UTestInst

class JcSpringTestExecutorDecoderApi(
    cp: JcClasspath
) : JcTestExecutorDecoderApi(cp) {

    fun addInstructions(instructions: List<UTestInst>) {
        this.instructions.addAll(instructions)
    }

    fun addInstruction(instruction: UTestInst) {
        this.instructions.add(instruction)
    }
}
