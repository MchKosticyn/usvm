package org.usvm.machine.state.concreteMemory.concreteMemoryRegions

import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedField
import org.usvm.UBoolExpr
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.field.UFieldLValue
import org.usvm.collection.field.UFieldsRegion
import org.usvm.collection.field.UFieldsRegionId
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.isTrue
import org.usvm.machine.JcContext
import org.usvm.machine.state.concreteMemory.JcConcreteMemoryBindings
import org.usvm.machine.state.concreteMemory.Marshall
import org.usvm.machine.state.concreteMemory.getFieldValue
import org.usvm.machine.state.concreteMemory.toJavaField
import org.usvm.memory.UMemoryRegion
import org.usvm.util.typedField

internal class JcConcreteFieldRegion<Sort : USort>(
    private val regionId: UFieldsRegionId<JcField, Sort>,
    private val ctx: JcContext,
    private val bindings: JcConcreteMemoryBindings,
    private var baseRegion: UFieldsRegion<JcField, Sort>,
    private val marshall: Marshall,
    private val ownership: MutabilityOwnership
) : UFieldsRegion<JcField, Sort>, JcConcreteRegion {

    private val jcField by lazy { regionId.field }
    private val javaField by lazy { jcField.toJavaField }
    private val isApproximation by lazy { javaField == null }
    //    private val isPrimitiveApproximation by lazy { isApproximation && jcField.name == "value" }
    private val sort by lazy { regionId.sort }
    private val typedField: JcTypedField by lazy { jcField.typedField }
    private val fieldType: JcType by lazy { typedField.type }
    private val isSyntheticClassField: Boolean by lazy { jcField == ctx.classTypeSyntheticField }

    private fun writeToBase(
        key: UFieldLValue<JcField, Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr
    ) {
        baseRegion = baseRegion.write(key, value, guard, ownership) as UFieldsRegion<JcField, Sort>
    }

    @Suppress("UNCHECKED_CAST")
    override fun read(key: UFieldLValue<JcField, Sort>): UExpr<Sort> {
        check(jcField == key.field)
        val ref = key.ref
        if (ref is UConcreteHeapRef && bindings.contains(ref.address)) {
            val address = ref.address
            if (isSyntheticClassField) {
                val type = bindings.virtToPhys(address) as Class<*>
                val jcType = ctx.cp.findTypeOrNull(type.typeName)!!
                jcType as JcRefType
                val allocated = bindings.allocateDefaultConcrete(jcType)!!
                return ctx.mkConcreteHeapRef(allocated) as UExpr<Sort>
            }

            if (!isApproximation) {
                val fieldObj = bindings.readClassField(address, javaField!!)
                return marshall.objToExpr(fieldObj, fieldType) // TODO: use reflect type? #CM
            }

            marshall.encode(address)
        }

        return baseRegion.read(key)
    }

    override fun write(
        key: UFieldLValue<JcField, Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): UMemoryRegion<UFieldLValue<JcField, Sort>, Sort> {
        check(this.ownership == ownership)
        check(jcField == key.field)
        val ref = key.ref
        if (!isSyntheticClassField && ref is UConcreteHeapRef && bindings.contains(ref.address)) {
            val address = ref.address
            if (!isApproximation) {
                val objValue = marshall.tryExprToObj(value, fieldType)
                val writeIsConcrete = objValue.hasValue && guard.isTrue
                if (writeIsConcrete && bindings.writeClassField(address, javaField!!, objValue.value))
                    return this
            }

            marshall.unmarshallClass(address)
        }

        writeToBase(key, value, guard)

        return this
    }

    @Suppress("UNCHECKED_CAST")
    fun unmarshallField(ref: UConcreteHeapRef, obj: Any) {
        val lvalue = UFieldLValue(sort, ref, jcField)
        val fieldObj = jcField.getFieldValue(obj)
        val rvalue = marshall.objToExpr<USort>(fieldObj, fieldType) as UExpr<Sort>
        writeToBase(lvalue, rvalue, ctx.trueExpr)
    }

    fun copy(
        bindings: JcConcreteMemoryBindings,
        marshall: Marshall,
        ownership: MutabilityOwnership
    ): JcConcreteFieldRegion<Sort> {
        return JcConcreteFieldRegion(
            regionId,
            ctx,
            bindings,
            baseRegion,
            marshall,
            ownership
        )
    }
}
