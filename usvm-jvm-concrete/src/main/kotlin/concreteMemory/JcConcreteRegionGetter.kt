package concreteMemory

import org.usvm.USort
import concreteMemory.concreteMemoryRegions.JcConcreteRegion
import org.usvm.memory.UMemoryRegionId

internal interface JcConcreteRegionGetter {
    fun <Key, Sort : USort> getConcreteRegion(regionId: UMemoryRegionId<Key, Sort>): JcConcreteRegion
}
