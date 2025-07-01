package util.database

import machine.interpreter.transformers.springjpa.JAVA_CLASS
import machine.interpreter.transformers.springjpa.JAVA_INIT
import machine.interpreter.transformers.springjpa.JAVA_STRING
import machine.interpreter.transformers.springjpa.JAVA_VOID
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.cfg.JcRawCallInst
import org.jacodb.api.jvm.cfg.JcRawCastExpr
import org.jacodb.api.jvm.cfg.JcRawClassConstant
import org.jacodb.api.jvm.cfg.JcRawLocalVar
import org.jacodb.api.jvm.cfg.JcRawNewExpr
import org.jacodb.api.jvm.cfg.JcRawSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcRawStaticCallExpr
import org.jacodb.api.jvm.cfg.JcRawVirtualCallExpr
import org.jacodb.api.jvm.ext.JAVA_OBJECT
import org.usvm.jvm.util.typeName
import org.usvm.jvm.util.typename

enum class JcValidateAnnotation(val annotationSimpleName: String, val validatorName: String) {

    // TODO: adds other enums validators!!
    NotNull("NotNull", "org.hibernate.validator.internal.constraintvalidators.bv.NotNullValidator"),
    NotBlank("NotBlank", "org.hibernate.validator.internal.constraintvalidators.bv.NotBlankValidator"),
    Digits("Digits", "org.hibernate.validator.internal.constraintvalidators.bv.DigitsValidatorForCharSequence");

    fun initializeValidator(ctx: JcMethodNewBodyContext, annot: JcAnnotation?) =
        if (annot == null || annot.values.isEmpty()) initValidator(ctx) else initializeValidatorWithBuilder(ctx, annot)

    fun initValidator(ctx: JcMethodNewBodyContext): JcRawLocalVar = with(ctx) {
        val typ = validatorName.typeName
        val validator = putValueToVar(JcRawNewExpr(typ), typ)

        val callExpr = JcRawSpecialCallExpr(typ, JAVA_INIT, emptyList(), JAVA_VOID.typeName, validator, emptyList())
        addInstruction { owner -> JcRawCallInst(owner, callExpr) }

        return validator
    }

    fun initializeValidatorWithBuilder(ctx: JcMethodNewBodyContext, annot: JcAnnotation): JcRawLocalVar = with(ctx) {
        val builderType = "org.hibernate.validator.internal.util.annotation.AnnotationDescriptor\$Builder".typeName
        val builder = putValueToVar(JcRawNewExpr(builderType), builderType)

        val annotTyp = annot.jcClass!!.typename
        val annotType = putValueToVar(JcRawClassConstant(annotTyp, JAVA_CLASS.typeName), JAVA_CLASS.typeName)

        val builderInit = JcRawSpecialCallExpr(
            builderType,
            JAVA_INIT,
            listOf(JAVA_CLASS.typeName),
            JAVA_VOID.typeName,
            builder,
            listOf(annotType)
        )
        addInstruction { owner -> JcRawCallInst(owner, builderInit) }

        annot.values.forEach { name, value ->
            val nameVar = resolveRawObject(name)
            val valueVar = resolveRawObject(value)

            val callExpr = JcRawVirtualCallExpr(
                builderType,
                "setAttribute",
                listOf(JAVA_STRING.typeName, JAVA_OBJECT.typeName),
                JAVA_VOID.typeName,
                builder,
                listOf(nameVar, valueVar)
            )
            addInstruction { owner -> JcRawCallInst(owner, callExpr) }
        }

        val descriptorType = "org.hibernate.validator.internal.util.annotation.AnnotationDescriptor".typeName
        val buildCall = JcRawVirtualCallExpr(
            builderType,
            "build",
            listOf(),
            descriptorType,
            builder,
            listOf()
        )
        val descriptor = putValueToVar(buildCall, descriptorType)

        val javaAnnotType = "java.lang.annotation.Annotation".typeName
        val createCall = JcRawStaticCallExpr(
            "org.hibernate.validator.internal.util.annotation.AnnotationFactory".typeName,
            "create",
            listOf(descriptorType),
            javaAnnotType,
            listOf(descriptor)
        )
        val annotConst = putValueToVar(createCall, javaAnnotType)

        val annotCasted = putValueToVar(JcRawCastExpr(annotTyp, annotConst), annotTyp)

        val validator = initValidator(ctx)

        val initCall = JcRawVirtualCallExpr(
            validatorName.typeName,
            "initialize",
            listOf(annotTyp),
            JAVA_VOID.typeName,
            validator,
            listOf(annotCasted)
        )
        addInstruction { owner -> JcRawCallInst(owner, initCall) }

        return validator
    }
}


fun JcField.getValidators() = annotations.mapNotNull { annot ->
    // TODO: adds other enums validators!!
    JcValidateAnnotation.entries.find { it.annotationSimpleName == annot.jcClass!!.simpleName }
        ?.let { annot to it }
}
