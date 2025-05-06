package util.database

import machine.interpreter.transformers.springjpa.JAVA_INIT
import machine.interpreter.transformers.springjpa.JAVA_STRING
import machine.interpreter.transformers.springjpa.JAVA_VOID
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcRawAssignInst
import org.jacodb.api.jvm.cfg.JcRawCallInst
import org.jacodb.api.jvm.cfg.JcRawFieldRef
import org.jacodb.api.jvm.cfg.JcRawLocalVar
import org.jacodb.api.jvm.cfg.JcRawNewExpr
import org.jacodb.api.jvm.cfg.JcRawSpecialCallExpr
import org.jacodb.impl.cfg.JcRawInt
import org.jacodb.impl.cfg.JcRawString
import org.usvm.jvm.util.typeName

interface ValidateAnnotation {
    fun initializeValidator(ctx: JcMethodNewBodyContext, annot: JcAnnotation?): JcRawLocalVar
}

enum class JcValidateAnnotation(val annotationSimpleName: String, val validatorName: String): ValidateAnnotation {

    NotNull("NotNull", "org.hibernate.validator.internal.constraintvalidators.bv.NotNullValidator") {
        override fun initializeValidator(ctx: JcMethodNewBodyContext, annot: JcAnnotation?) = initValidator(ctx)
    },

    NotBlank("NotBlank", "org.hibernate.validator.internal.constraintvalidators.bv.NotBlankValidator") {
        override fun initializeValidator(ctx: JcMethodNewBodyContext, annot: JcAnnotation?) = initValidator(ctx)
    },

    Digits("Digits", "org.hibernate.validator.internal.constraintvalidators.bv.DigitsValidatorForCharSequence") {
        override fun initializeValidator(ctx: JcMethodNewBodyContext, annot: JcAnnotation?): JcRawLocalVar = with(ctx) {
            val validator = initValidator(ctx)
            val annotValues = annot?.values!!

            val maxIntegerLength = annotValues["integer"]?.let { it as? Int }!!
            val maxFractionLength = annotValues["fraction"]?.let { it as? Int }!!

            val intRef = JcRawFieldRef(validator, validatorName.typeName, "integer", "int".typeName)
            val fracRef = JcRawFieldRef(validator, validatorName.typeName, "fraction", "int".typeName)
            addInstruction { owner -> JcRawAssignInst(owner, intRef, JcRawInt(maxIntegerLength)) }
            addInstruction { owner -> JcRawAssignInst(owner, fracRef, JcRawInt(maxFractionLength)) }

            return validator
        }
    },

    DateTimeFormat("DateTimeFormat", "org.hibernate.validator.internal.constraintvalidators.bv.PatternValidator") {
        override fun initializeValidator(ctx: JcMethodNewBodyContext, annot: JcAnnotation?): JcRawLocalVar = with(ctx) {
            val validator = initValidator(ctx)
            val escapedRegexp = annot?.values?.get("pattern")?.let { it as? String }!!
            val regRef = JcRawFieldRef(validator, validatorName.typeName, "escapedRegexp", JAVA_STRING.typeName)
            addInstruction { owner -> JcRawAssignInst(owner, regRef, JcRawString(escapedRegexp)) }
            return validator
        }
    };

    fun initValidator(ctx: JcMethodNewBodyContext): JcRawLocalVar = with(ctx) {
        val typ = validatorName.typeName

        val validator = newVar(typ)
        val newValue = JcRawNewExpr(typ)
        addInstruction { owner -> JcRawAssignInst(owner, validator, newValue) }

        val callExpr = JcRawSpecialCallExpr(typ, JAVA_INIT, listOf(), JAVA_VOID.typeName, validator, listOf())
        addInstruction { owner -> JcRawCallInst(owner, callExpr) }

        return validator
    }
}
