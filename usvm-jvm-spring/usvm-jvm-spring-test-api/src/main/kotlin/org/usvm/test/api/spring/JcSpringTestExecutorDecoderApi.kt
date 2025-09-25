package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.usvm.test.api.JcTestExecutorDecoderApi
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMethodCall

class JcSpringTestExecutorDecoderApi(
    cp: JcClasspath
) : JcTestExecutorDecoderApi(cp) {

    fun addInstructions(instructions: List<UTestInst>) {
        this.instructions.addAll(instructions)
    }

    fun addInstruction(instruction: UTestInst) {
        this.instructions.add(instruction)
    }

    override fun setField(field: JcField, instance: UTestExpression, value: UTestExpression) {
        val lombokSetter = field.lombokLikeSetterOrNull

        if (lombokSetter != null)
            addInstruction(UTestMethodCall(instance, lombokSetter, listOf(value)))
        else {
            super.setField(field, instance, value)
        }
    }

    private val JcField.lombokLikeSetterOrNull: JcMethod?
        get() = enclosingClass.declaredMethods.singleOrNull {
            it.name.lowercase() == "set${name.lowercase()}" && it.parameters.singleOrNull()?.type == type && it.isPublic
        }


    override fun getField(field: JcField, instance: UTestExpression): UTestExpression {
        val lombokGetter = field.lombokLikeGetterOrNull
        return if (lombokGetter != null)
            UTestMethodCall(instance, lombokGetter, listOf())
        else
            super.getField(field, instance)
    }

    private val JcField.lombokLikeGetterOrNull: JcMethod?
        get() = enclosingClass.declaredMethods.singleOrNull {
            it.name.lowercase() == "get${name.lowercase()}" && it.returnType == type && it.isPublic
        }
}
