package machine.concreteMemory.concreteMemoryRegions

import machine.JcConcreteMemoryClassLoader
import machine.concreteMemory.Marshall
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.interpreter.statics.JcStaticFieldLValue
import org.usvm.machine.interpreter.statics.JcStaticFieldRegionId
import org.usvm.machine.interpreter.statics.JcStaticFieldsMemoryRegion
import utils.getStaticFieldValue
import utils.isInternalType
import utils.setStaticFieldValue
import utils.toJavaField
import utils.typedField

internal class JcConcreteStaticFieldsRegion<Sort : USort>(
    private val regionId: JcStaticFieldRegionId<Sort>,
    private var baseRegion: JcStaticFieldsMemoryRegion<Sort>,
    private val marshall: Marshall,
    private val ownership: MutabilityOwnership,
    private val writtenFields: MutableSet<JcStaticFieldLValue<Sort>> = mutableSetOf()
) : JcStaticFieldsMemoryRegion<Sort>(regionId.sort), JcConcreteRegion {

    // TODO: redo #CM
    override fun read(key: JcStaticFieldLValue<Sort>): UExpr<Sort> {
        val field = key.field
        val javaField = field.toJavaField
        if (field.enclosingClass.isInternalType || javaField == null)
            return baseRegion.read(key)

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
        writtenFields.add(key)
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
        val javaField = field.toJavaField
        // TODO: should mutate every field or filter some fields?
        if (javaField == null) {
            writeToRegion(key, value, guard, ownership)
            return this
        }

        val fieldType = field.typedField.type
        val concreteValue = marshall.tryExprToFullyConcreteObj(value, fieldType)
        if (concreteValue.isNone) {
            writeToRegion(key, value, guard, ownership)
            return this
        }
        check(JcConcreteMemoryClassLoader.isLoaded(field.enclosingClass))
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

    fun fieldsWithValues(): MutableMap<JcField, UExpr<Sort>> {
        val result: MutableMap<JcField, UExpr<Sort>> = mutableMapOf()
        for (key in writtenFields) {
            val value = baseRegion.read(key)
            result[key.field] = value
        }

        return result
    }

    fun copy(
        marshall: Marshall,
        ownership: MutabilityOwnership
    ): JcConcreteStaticFieldsRegion<Sort> {
        return JcConcreteStaticFieldsRegion(
            regionId,
            baseRegion,
            marshall,
            ownership,
            writtenFields.toMutableSet()
        )
    }
}
