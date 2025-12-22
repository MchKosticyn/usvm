package machine.memory

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.usvm.UBoolExpr
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.collections.immutable.getOrDefault
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.collections.immutable.persistentHashMapOf
import org.usvm.machine.jctx
import org.usvm.memory.ULValue
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.guardedWrite
import org.usvm.sampleUValue

class JcMockedMethod(
    val method: String,
    val enclosingClass: String
) {
    val str = enclosingClass + method
    fun print() {
//        val str = enclosingClass + method
        print(str)
    }
}

data class JcMockedMethodsValue<Sort : USort>(
    val mockedMethod: JcMockedMethod,
    override val sort: Sort,
    val type: JcType,
    val method: JcMethod
) : ULValue<JcMockedMethodsValue<Sort>, Sort> {
    fun print() {
        mockedMethod.print()
    }
    override val memoryRegionId: UMemoryRegionId<JcMockedMethodsValue<Sort>, Sort> = JcMockedMethodsRegionId(sort, type)

    override val key: JcMockedMethodsValue<Sort>
        get() = this
}

data class JcMockedMethodsRegionId<Sort : USort>(
    override val sort: Sort,
    val type: JcType
) : UMemoryRegionId<JcMockedMethodsValue<Sort>, Sort> {
    override fun emptyRegion(): UMemoryRegion<JcMockedMethodsValue<Sort>, Sort> = JcMockedMethodsRegion(sort, type)
}

open class JcMockedMethodsRegion<Sort : USort>(
    private val sort: Sort,
    private val type: JcType,
    private val mockedMethods: UPersistentHashMap<String, UPersistentHashMap<String, UExpr<Sort>>> = persistentHashMapOf()
) : UMemoryRegion<JcMockedMethodsValue<Sort>, Sort> {
    override fun read(key: JcMockedMethodsValue<Sort>): UExpr<Sort> {
        val mockedMethod = key.mockedMethod
        val field = mockedMethod.method
        val ret = mockedMethods[mockedMethod.enclosingClass]?.get(field)
        return ret ?: JcMockedMethodsReading(sort.jctx, key.memoryRegionId as JcMockedMethodsRegionId, mockedMethod, type, key.method, sort)
    }

    override fun write(
        key: JcMockedMethodsValue<Sort>,
        value: UExpr<Sort>,
        guard: UBoolExpr,
        ownership: MutabilityOwnership
    ): UMemoryRegion<JcMockedMethodsValue<Sort>, Sort> {
        val mockedMethod = key.mockedMethod
        val enclosingClass = mockedMethod.enclosingClass
        val classFields = mockedMethods.getOrDefault(enclosingClass, persistentHashMapOf())

        val newFieldValues = classFields.guardedWrite(key.mockedMethod.method, value, guard, ownership) { key.sort.sampleUValue() }
        val newFieldsByClass = mockedMethods.put(enclosingClass, newFieldValues, ownership)
        return JcMockedMethodsRegion(sort, type, newFieldsByClass)
    }
}
