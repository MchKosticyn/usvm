package machine.concreteMemory

import machine.concreteMemory.concreteMemoryRegions.JcConcreteRegion
import org.usvm.USort
import org.usvm.memory.UMemoryRegionId

internal interface JcConcreteRegionGetter {
    fun <Key, Sort : USort> getConcreteRegion(regionId: UMemoryRegionId<Key, Sort>): JcConcreteRegion
}
