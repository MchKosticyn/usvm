package machine.state.pinnedValues

import io.ksmt.utils.asExpr
import org.jacodb.api.jvm.JcType
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.api.makeNullableSymbolicRef
import org.usvm.api.makeSymbolicRef
import org.usvm.machine.interpreter.JcStepScope

abstract class JcSpringRawPinnedValues<V> (
    protected var pinnedValues: Map<JcPinnedKey, V>
) {
    fun getMap() = pinnedValues

    fun getValue(key: JcPinnedKey): V? {
        return pinnedValues[key]
    }

    fun setValue(key: JcPinnedKey, value: V) {
        pinnedValues = pinnedValues.filter { it.key != key }
        pinnedValues += key to value
    }

    // TODO: Find solution without unchecked cast #AA
    @Suppress("UNCHECKED_CAST")
    fun <K : JcPinnedKey> getValuesOfSource(source: JcSpringPinnedValueSource): Map<K, V> {
        return pinnedValues
            .filter { it.key.getSource() == source }
            .map { (k, v) -> k as K to v }
            .toMap()
    }
}

class JcSpringPinnedValues : JcSpringRawPinnedValues<JcPinnedValue>(emptyMap()) {
    fun createAndPut(
        key: JcPinnedKey, 
        type: JcType, 
        scope: JcStepScope, 
        sort: USort, 
        nullable: Boolean = true
    ): JcPinnedValue? {
        val newValueExpr =
            if (nullable) scope.makeNullableSymbolicRef(type)?.asExpr(sort)
            else scope.makeSymbolicRef(type)?.asExpr(sort)

        if (newValueExpr == null) {
            println("Error creating symbolic value!")
            return null
        }

        val newValue = JcPinnedValue(newValueExpr, type)
        setValue(key, newValue)

        return newValue
    }

    fun createIfAbsent(
        key: JcPinnedKey, 
        type: JcType, 
        scope: JcStepScope, 
        sort: USort, 
        nullable: Boolean = true
    ): JcPinnedValue? {
        val existingValue = getValue(key)
        if (existingValue != null)
            return existingValue
        return createAndPut(key, type, scope, sort, nullable)
    }

    fun getKeyOfExpr(value: UExpr<out USort>): JcPinnedKey? {
        val pair = pinnedValues.entries.firstOrNull { it.value.getExpr() == value }
        if (pair == null)
            return null
        return pair.key
    }

    fun copy(): JcSpringPinnedValues {
        return JcSpringPinnedValues().also { it.pinnedValues = pinnedValues }
    }
}
