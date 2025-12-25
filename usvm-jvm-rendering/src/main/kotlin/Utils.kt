fun <K, V> Map<K, V>.partitionByKey(predicate: (K) -> Boolean): Pair<Map<K, V>, Map<K, V>> {
    val isTrue = mutableMapOf<K, V>()
    val isFalse = mutableMapOf<K, V>()

    for ((k, v) in entries) {
        if (predicate(k)) {
            isTrue.put(k, v)
        } else {
            isFalse.put(k, v)
        }
    }
    return Pair(isTrue, isFalse)
}
