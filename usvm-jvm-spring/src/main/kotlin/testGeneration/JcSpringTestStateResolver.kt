package testGeneration

import machine.state.pinnedValues.JcPinnedValue
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedField
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.ext.constructors
import org.jacodb.api.jvm.ext.objectType
import org.usvm.UAddressSort
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.UHeapRef
import org.usvm.api.util.JcTestStateResolver
import org.usvm.collection.field.UFieldLValue
import org.usvm.machine.JcContext
import org.usvm.memory.UReadOnlyMemory
import org.usvm.model.UModelBase
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestConstructorCall
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.spring.JcSpringTestExecutorDecoderApi
import utils.JcConcreteTestStateResolver

class JcSpringTestStateResolver(
    ctx: JcContext,
    model: UModelBase<JcType>,
    finalStateMemory: UReadOnlyMemory<JcType>,
    method: JcTypedMethod,
) : JcConcreteTestStateResolver<UTestExpression>(ctx, model, finalStateMemory, method) {

    override val decoderApi = JcSpringTestExecutorDecoderApi(ctx.cp)

    override var resolveMode: ResolveMode = ResolveMode.CURRENT

    override val shouldExtendMapWithObjects: Boolean = false

    override fun <R> withMode(resolveMode: ResolveMode, body: JcTestStateResolver<UTestExpression>.() -> R): R {
        check(resolveMode == ResolveMode.CURRENT && this.resolveMode == ResolveMode.CURRENT)
        return body()
    }

    override fun shouldIgnoreField(typedField: JcTypedField): Boolean {
        val field = typedField.field
        return super.shouldIgnoreField(typedField)
                || field.annotations.any { it.name == "jakarta.persistence.GeneratedValue" }
    }

    override fun allocateClassInstance(type: JcClassType): UTestExpression {
        val noArgsConstructor = type.constructors.singleOrNull {
            it.isPublic && it.parameters.isEmpty()
        }

        return if (noArgsConstructor != null)
            UTestConstructorCall(noArgsConstructor.method, listOf())
        else
            UTestAllocateMemoryCall(type.jcClass)
    }

    fun resolvePinnedValue(value: JcPinnedValue) = resolveExpr(value.getExpr(), value.getType())

    override fun resolveObject(ref: UConcreteHeapRef, heapRef: UHeapRef, type: JcClassType): UTestExpression {
        val currentRef = if (resolveMode == ResolveMode.CURRENT) heapRef else ref

        val allArgsConstructor = type.allArgsConstructorInvocationOrNull(currentRef)

        return allArgsConstructor ?: super.resolveObject(ref, heapRef, type)
    }

    private fun JcClassType.allArgsConstructorInvocationOrNull(ref: UExpr<UAddressSort>): UTestExpression? {
        if (superType != classpath.objectType)
            return null

        val constructingFields = declaredFields.filterNot { shouldIgnoreField(it) }
        val constructingFieldsNames = constructingFields.map { it.name.lowercase() }

        val ctor = constructors.singleOrNull { ctor ->
            ctor.parameters.size == constructingFields.size &&
                    ctor.parameters.all { param -> param.name?.let { it.lowercase() in constructingFieldsNames } ?: false
            }
        }?.method

        if (ctor == null) return null

        val fieldsToValues = constructingFields.associate { field ->
            val lvalue = UFieldLValue(ctx.typeToSort(field.type), ref, field.field)
            field.name to resolveLValue(lvalue, field.type)
        }

        return UTestConstructorCall(ctor, ctor.parameters.map { fieldsToValues[it.name!!]!! })
    }
}
