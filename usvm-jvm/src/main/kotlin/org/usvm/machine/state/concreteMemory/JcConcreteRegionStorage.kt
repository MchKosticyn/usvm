package org.usvm.machine.state.concreteMemory

import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedField
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collection.array.UArrayRegionId
import org.usvm.collection.array.length.UArrayLengthsRegionId
import org.usvm.collection.field.UFieldsRegionId
import org.usvm.collection.map.length.UMapLengthRegionId
import org.usvm.collection.map.ref.URefMapRegionId
import org.usvm.collection.set.ref.URefSetRegionId
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.machine.JcContext
import org.usvm.machine.USizeSort
import org.usvm.machine.interpreter.JcLambdaCallSiteRegionId
import org.usvm.machine.interpreter.statics.JcStaticFieldRegionId
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteArrayLengthRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteArrayRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteCallSiteLambdaRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteFieldRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteMapLengthRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteRefMapRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteRefSetRegion
import org.usvm.machine.state.concreteMemory.concreteMemoryRegions.JcConcreteStaticFieldsRegion
import org.usvm.sizeSort

internal class JcConcreteRegionStorage(
    private val ctx: JcContext,
    private val regionGetter: JcConcreteRegionGetter,
    private val fieldRegions: MutableMap<JcField, JcConcreteFieldRegion<*>> = mutableMapOf(),
    private val arrayRegions: MutableMap<JcType, JcConcreteArrayRegion<*>> = mutableMapOf(),
    private val arrayLengthRegions: MutableMap<JcType, JcConcreteArrayLengthRegion> = mutableMapOf(),
    private val mapRegions: MutableMap<JcType, JcConcreteRefMapRegion<*>> = mutableMapOf(),
    private val mapLengthRegions: MutableMap<JcType, JcConcreteMapLengthRegion> = mutableMapOf(),
    private val setRegions: MutableMap<JcType, JcConcreteRefSetRegion> = mutableMapOf(),
    private val staticFieldsRegions: MutableMap<USort, JcConcreteStaticFieldsRegion<*>> = mutableMapOf(),
    private var lambdaCallSiteRegion: JcConcreteCallSiteLambdaRegion? = null
) {

    fun getFieldRegion(typedField: JcTypedField): JcConcreteFieldRegion<*> {
        val field = typedField.field
        return fieldRegions.getOrPut(field) {
            val sort = ctx.typeToSort(typedField.type)
            val regionId = UFieldsRegionId(field, sort)
            regionGetter.getConcreteRegion(regionId) as JcConcreteFieldRegion<*>
        }
    }

    fun getFieldRegion(regionId: UFieldsRegionId<*, *>): JcConcreteFieldRegion<*> {
        return fieldRegions.getOrPut(regionId.field as JcField) {
            regionGetter.getConcreteRegion(regionId) as JcConcreteFieldRegion<*>
        }
    }

    fun getArrayRegion(desc: JcType, sort: USort): JcConcreteArrayRegion<*> {
        return arrayRegions.getOrPut(desc) {
            val regionId = UArrayRegionId<JcType, USort, USizeSort>(desc, sort)
            regionGetter.getConcreteRegion(regionId) as JcConcreteArrayRegion<*>
        }
    }

    fun getArrayRegion(regionId: UArrayRegionId<*, *, *>): JcConcreteArrayRegion<*> {
        return arrayRegions.getOrPut(regionId.arrayType as JcType) {
            regionGetter.getConcreteRegion(regionId) as JcConcreteArrayRegion<*>
        }
    }

    fun getArrayLengthRegion(desc: JcType): JcConcreteArrayLengthRegion {
        return arrayLengthRegions.getOrPut(desc) {
            val regionId = UArrayLengthsRegionId(ctx.sizeSort, desc)
            regionGetter.getConcreteRegion(regionId) as JcConcreteArrayLengthRegion
        }
    }

    fun getArrayLengthRegion(regionId: UArrayLengthsRegionId<*, *>): JcConcreteArrayLengthRegion {
        return arrayLengthRegions.getOrPut(regionId.arrayType as JcType) {
            regionGetter.getConcreteRegion(regionId) as JcConcreteArrayLengthRegion
        }
    }

    fun getMapRegion(mapType: JcType, valueSort: USort): JcConcreteRefMapRegion<*> {
        return mapRegions.getOrPut(mapType) {
            val regionId = URefMapRegionId(valueSort, mapType)
            regionGetter.getConcreteRegion(regionId) as JcConcreteRefMapRegion<*>
        }
    }

    fun getMapRegion(regionId: URefMapRegionId<*, *>): JcConcreteRefMapRegion<*> {
        return mapRegions.getOrPut(regionId.mapType as JcType) {
            regionGetter.getConcreteRegion(regionId) as JcConcreteRefMapRegion<*>
        }
    }

    fun getMapLengthRegion(mapType: JcType): JcConcreteMapLengthRegion {
        return mapLengthRegions.getOrPut(mapType) {
            val regionId = UMapLengthRegionId(ctx.sizeSort, mapType)
            regionGetter.getConcreteRegion(regionId) as JcConcreteMapLengthRegion
        }
    }

    fun getMapLengthRegion(regionId: UMapLengthRegionId<*, *>): JcConcreteMapLengthRegion {
        return mapLengthRegions.getOrPut(regionId.mapType as JcType) {
            regionGetter.getConcreteRegion(regionId) as JcConcreteMapLengthRegion
        }
    }

    fun getSetRegion(setType: JcType): JcConcreteRefSetRegion {
        return setRegions.getOrPut(setType) {
            val regionId = URefSetRegionId(setType, ctx.boolSort)
            regionGetter.getConcreteRegion(regionId) as JcConcreteRefSetRegion
        }
    }

    fun getSetRegion(regionId: URefSetRegionId<*>): JcConcreteRefSetRegion {
        return setRegions.getOrPut(regionId.setType as JcType) {
            regionGetter.getConcreteRegion(regionId) as JcConcreteRefSetRegion
        }
    }

    fun getStaticFieldsRegion(regionId: JcStaticFieldRegionId<*>): JcConcreteStaticFieldsRegion<*> {
        return staticFieldsRegions.getOrPut(regionId.sort) {
            regionGetter.getConcreteRegion(regionId) as JcConcreteStaticFieldsRegion<*>
        }
    }

    fun getLambdaCallSiteRegion(regionId: JcLambdaCallSiteRegionId): JcConcreteCallSiteLambdaRegion {
        if (lambdaCallSiteRegion != null)
            return lambdaCallSiteRegion!!
        lambdaCallSiteRegion = regionGetter.getConcreteRegion(regionId) as JcConcreteCallSiteLambdaRegion
        return lambdaCallSiteRegion!!
    }

    fun allMutatedStaticFields(): Map<JcField, UExpr<out USort>> {
        val result: MutableMap<JcField, UExpr<*>> = mutableMapOf()
        staticFieldsRegions.forEach { (_, staticRegion) ->
            result.putAll(staticRegion.fieldsWithValues())
        }

        return result
    }

    fun copy(
        bindings: JcConcreteMemoryBindings,
        regionGetter: JcConcreteRegionGetter,
        marshall: Marshall,
        ownership: MutabilityOwnership
    ): JcConcreteRegionStorage {
        val newFieldRegions: MutableMap<JcField, JcConcreteFieldRegion<*>> = mutableMapOf()
        for ((k, v) in fieldRegions)
            newFieldRegions[k] = v.copy(bindings, marshall, ownership)
        val newArrayRegions: MutableMap<JcType, JcConcreteArrayRegion<*>> = mutableMapOf()
        for ((k, v) in arrayRegions)
            newArrayRegions[k] = v.copy(bindings, marshall, ownership)
        val newArrayLengthRegions: MutableMap<JcType, JcConcreteArrayLengthRegion> = mutableMapOf()
        for ((k, v) in arrayLengthRegions)
            newArrayLengthRegions[k] = v.copy(bindings, marshall, ownership)
        val newMapRegions: MutableMap<JcType, JcConcreteRefMapRegion<*>> = mutableMapOf()
        for ((k, v) in mapRegions)
            newMapRegions[k] = v.copy(bindings, marshall, ownership)
        val newMapLengthRegions: MutableMap<JcType, JcConcreteMapLengthRegion> = mutableMapOf()
        for ((k, v) in mapLengthRegions)
            newMapLengthRegions[k] = v.copy(bindings, marshall, ownership)
        val newSetRegions: MutableMap<JcType, JcConcreteRefSetRegion> = mutableMapOf()
        for ((k, v) in setRegions)
            newSetRegions[k] = v.copy(bindings, marshall, ownership)
        val newStaticFieldsRegions: MutableMap<USort, JcConcreteStaticFieldsRegion<*>> = mutableMapOf()
        for ((k, v) in staticFieldsRegions)
            newStaticFieldsRegions[k] = v.copy(marshall, ownership)
        val newLambdaCallSiteRegion = lambdaCallSiteRegion?.copy(bindings, marshall, ownership)
        return JcConcreteRegionStorage(
            ctx,
            regionGetter,
            newFieldRegions,
            newArrayRegions,
            newArrayLengthRegions,
            newMapRegions,
            newMapLengthRegions,
            newSetRegions,
            newStaticFieldsRegions,
            newLambdaCallSiteRegion
        )
    }
}
