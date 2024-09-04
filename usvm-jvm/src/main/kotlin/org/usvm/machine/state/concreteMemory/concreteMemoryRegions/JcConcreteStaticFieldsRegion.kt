package org.usvm.machine.state.concreteMemory.concreteMemoryRegions

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.jacodb.approximation.JcEnrichedVirtualField
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.util.JcConcreteMemoryClassLoader
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.interpreter.statics.JcStaticFieldLValue
import org.usvm.machine.interpreter.statics.JcStaticFieldRegionId
import org.usvm.machine.interpreter.statics.JcStaticFieldsMemoryRegion
import org.usvm.machine.interpreter.statics.staticFieldsInitializedFlagField
import org.usvm.machine.state.concreteMemory.Marshall
import org.usvm.machine.state.concreteMemory.getStaticFieldValue
import org.usvm.machine.state.concreteMemory.toJavaField
import org.usvm.util.typedField

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
        if (field is JcEnrichedVirtualField || field.name == staticFieldsInitializedFlagField.name)
            return baseRegion.read(key)

        check(JcConcreteMemoryClassLoader.isLoaded(field.enclosingClass))
        val fieldType = field.typedField.type
        val javaField = field.toJavaField!!
        val value = javaField.getStaticFieldValue()
        // TODO: differs from jcField.getFieldValue(JcConcreteMemoryClassLoader, null) #CM
//        val value = field.getFieldValue(JcConcreteMemoryClassLoader, null)
        return marshall.objToExpr(value, fieldType)
    }

    override fun write(
        key: JcStaticFieldLValue<Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): JcConcreteStaticFieldsRegion<Sort> {
        check(this.ownership == ownership)
        // TODO: check isWritable and set #CM
        writtenFields.add(key)
        // TODO: mutate concrete statics #CM
        baseRegion = baseRegion.write(key, value, guard, ownership)
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
