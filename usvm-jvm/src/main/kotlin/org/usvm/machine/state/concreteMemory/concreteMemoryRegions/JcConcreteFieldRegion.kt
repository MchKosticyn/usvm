package org.usvm.machine.state.concreteMemory.concreteMemoryRegions

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedField
import org.jacodb.api.jvm.ext.isSubClassOf
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
import org.usvm.machine.state.concreteMemory.allInstanceFields
import org.usvm.machine.state.concreteMemory.getFieldValue
import org.usvm.machine.state.concreteMemory.isInternalType
import org.usvm.machine.state.concreteMemory.toJavaField
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.mapWithStaticAsConcrete
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
    private fun readConcrete(ref: UConcreteHeapRef, key: UFieldLValue<JcField, Sort>): UExpr<Sort> {
        if (bindings.contains(ref.address)) {
            val address = ref.address
            if (isSyntheticClassField) {
                val type = bindings.virtToPhys(address) as Class<*>
                val jcType = ctx.cp.findTypeOrNull(type.typeName)!!
                jcType as JcRefType
                val allocated = bindings.allocateDefaultConcrete(jcType)!!
                return ctx.mkConcreteHeapRef(allocated) as UExpr<Sort>
            }

            if (!isApproximation) {
                val (success, fieldObj) = bindings.readClassField(address, javaField!!)
                if (success)
                    // TODO: use reflect type? #CM
                    return marshall.objToExpr(fieldObj, fieldType)
            }

            // Not unmarshalling during unreachable reads
            val type = bindings.typeOf(address) as JcClassType
            if (type.jcClass.isSubClassOf(jcField.enclosingClass)) {
                marshall.encode(address)
            } else {
                println("[WARNING] unreachable read")
            }
        }

        return baseRegion.read(key)
    }

    override fun read(key: UFieldLValue<JcField, Sort>): UExpr<Sort> {
        check(jcField == key.field)
        return key.ref.mapWithStaticAsConcrete(
            concreteMapper = { readConcrete(it, key) },
            symbolicMapper = { baseRegion.read(key) },
            ignoreNullRefs = true
        )
    }

    override fun write(
        key: UFieldLValue<JcField, Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): UMemoryRegion<UFieldLValue<JcField, Sort>, Sort> {
        check(this.ownership == ownership)
        check(jcField == key.field)
        val ref = handleRefForWrite(key.ref, guard) {
            val type = bindings.typeOf(it) as JcClassType
            if (type.jcClass.isSubClassOf(jcField.enclosingClass) && !isSyntheticClassField)
                marshall.unmarshallClass(it)
        }

        if (ref == null || isSyntheticClassField) {
            writeToBase(key, value, guard)
            return this
        }

        if (ref is UConcreteHeapRef && bindings.contains(ref.address)) {
            val address = ref.address
            if (!isApproximation) {
                val objValue = marshall.tryExprToObj(value, fieldType)
                val writeIsConcrete = objValue.isSome && guard.isTrue
                if (writeIsConcrete && bindings.writeClassField(address, javaField!!, objValue.getOrThrow())) {
                    return this
                }
            }

            // Not unmarshalling during unreachable writes
            val type = bindings.typeOf(address) as JcClassType
            if (type.jcClass.isSubClassOf(jcField.enclosingClass)) {
                marshall.unmarshallClass(address)
            } else {
                println("[WARNING] unreachable write")
            }
        }

        writeToBase(key, value, guard)

        return this
    }

    @Suppress("UNCHECKED_CAST")
    fun unmarshallField(ref: UConcreteHeapRef, obj: Any) {
        val type = obj.javaClass
        val field =
            if (type.isInternalType)
                obj.javaClass.allInstanceFields.find { it.name == jcField.name }
                    ?: error("Could not find field '${jcField.name}'")
            else javaField!!
        val lvalue = UFieldLValue(sort, ref, jcField)
        val fieldObj = field.getFieldValue(obj)
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
