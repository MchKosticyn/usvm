package machine.state.concreteMemory.concreteMemoryRegions

import machine.JcConcreteMemoryClassLoader
import machine.state.concreteMemory.Marshall
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.jvm.util.typedField
import org.usvm.machine.JcContext
import org.usvm.machine.interpreter.statics.JcStaticFieldLValue
import org.usvm.machine.interpreter.statics.JcStaticFieldRegionId
import org.usvm.machine.interpreter.statics.JcStaticFieldsMemoryRegion
import utils.getStaticFieldValue
import utils.isInternalType
import utils.setStaticFieldValue
import utils.toJavaField
import java.lang.reflect.Field

internal class JcConcreteStaticFieldsRegion<Sort : USort>(
    private val ctx: JcContext,
    private val regionId: JcStaticFieldRegionId<Sort>,
    private var baseRegion: JcStaticFieldsMemoryRegion<Sort>,
    private val marshall: Marshall,
    private val ownership: MutabilityOwnership,
    private val symbolicFields: MutableSet<JcField> = mutableSetOf(),
) : JcStaticFieldsMemoryRegion<Sort>(regionId.sort), JcConcreteRegion {

    private val JcField.tryToJavaField: Field? get() {
        check(isStatic)
        if (enclosingClass.isInternalType)
            return null

        return toJavaField
    }

    // TODO: redo #CM
    override fun read(key: JcStaticFieldLValue<Sort>): UExpr<Sort> {
        val field = key.field
        if (symbolicFields.contains(field))
            return baseRegion.read(key)

        val javaField = field.tryToJavaField
            ?: return baseRegion.read(key)

        check(JcConcreteMemoryClassLoader.isLoaded(field.enclosingClass))
        val fieldType = field.typedField.type
        val value = javaField.getStaticFieldValue()
        // TODO: differs from jcField.getFieldValue(JcConcreteMemoryClassLoader, null) #CM
//        val value = field.getFieldValue(JcConcreteMemoryClassLoader, null)
        return marshall.objToExpr(value, fieldType)
    }

    private fun writeToRegion(
        key: JcStaticFieldLValue<Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ) {
        baseRegion = baseRegion.write(key, value, guard, ownership)
    }

    override fun write(
        key: JcStaticFieldLValue<Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): JcConcreteStaticFieldsRegion<Sort> {
        check(this.ownership == ownership)
        val field = key.field
        val javaField = field.tryToJavaField
        // TODO: should mutate every field or filter some fields?
        if (javaField == null) {
            writeToRegion(key, value, guard, ownership)
            return this
        }

        val fieldType = field.typedField.type
        val concreteValue = marshall.tryExprToFullyConcreteObj(value, fieldType)
        if (concreteValue.isNone) {
            symbolicFields.add(field)
            writeToRegion(key, value, guard, ownership)
            return this
        }
        check(JcConcreteMemoryClassLoader.isLoaded(field.enclosingClass))
        symbolicFields.remove(field)
        javaField.setStaticFieldValue(concreteValue.getOrThrow())

        return this
    }

    override fun mutatePrimitiveStaticFieldValuesToSymbolic(
        enclosingClass: JcClassOrInterface,
        ownership: MutabilityOwnership
    ) {
        check(this.ownership == ownership)
        // No symbolic statics
    }

    @Suppress("UNCHECKED_CAST")
    fun fieldsWithValues(): MutableMap<JcField, UExpr<Sort>> {
        val result: MutableMap<JcField, UExpr<Sort>> = mutableMapOf()
        for (field in symbolicFields) {
            val fieldType = ctx.cp.findTypeOrNull(field.type.typeName) ?: continue
            val sort = ctx.typeToSort(fieldType)
            val key = JcStaticFieldLValue(field, sort as Sort)
            val value = baseRegion.read(key)
            result[field] = value
        }

        return result
    }

    fun copy(
        marshall: Marshall,
        ownership: MutabilityOwnership
    ): JcConcreteStaticFieldsRegion<Sort> {
        return JcConcreteStaticFieldsRegion(
            ctx,
            regionId,
            baseRegion,
            marshall,
            ownership,
            symbolicFields.toMutableSet()
        )
    }
}
