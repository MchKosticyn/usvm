package machine.state.concreteMemory

import machine.state.concreteMemory.concreteMemoryRegions.JcConcreteRegion
import org.usvm.USort
import org.usvm.memory.UMemoryRegionId

internal interface JcConcreteRegionGetter {
    fun <Key, Sort : USort> getConcreteRegion(regionId: UMemoryRegionId<Key, Sort>): JcConcreteRegion
}
