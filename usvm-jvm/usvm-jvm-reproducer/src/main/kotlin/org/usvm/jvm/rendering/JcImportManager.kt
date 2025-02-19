package org.usvm.jvm.rendering

class JcImportManager(importList: List<String>) {
    constructor() : this(listOf())


    fun tryAdd(fullName: String, simpleName: String) {
        simpleToFull.putIfAbsent(simpleName, fullName)
    }

    fun tryAdd(fullName: String) = tryAdd(fullName, simpleNameFor(fullName))

    private fun simpleNameFor(v: String) = v.split(".").last()
    private val simpleToFull: MutableMap<String, String> = importList.associateByTo(mutableMapOf()) {
        it.split(".").last()
    }

    fun fullToSimple() = simpleToFull.entries.associate { e -> e.value to e.key }
}
