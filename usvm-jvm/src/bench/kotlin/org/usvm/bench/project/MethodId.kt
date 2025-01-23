package org.usvm.bench.project

import org.jacodb.api.jvm.JcMethod

data class MethodId(
    val className: String,
    val descriptor: String,
    val methodName: String
) {
    constructor(method: JcMethod) : this(method.enclosingClass.name, method.withAsmNode { it.desc } ?: "nodesc", method.name)

    val encodedString = "$className|$descriptor|$methodName"

    override fun toString() = encodedString

    companion object {
        fun decodeFromString(methodIdString: String): MethodId {
            val splitStrings = methodIdString.split("|")
            return MethodId(
                className = splitStrings[0],
                descriptor = splitStrings[1],
                methodName = splitStrings[2]
            )
        }
    }
}
