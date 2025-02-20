package org.usvm.machine.state.pinnedValues

import java.util.*

class JcSpringPinnedValueKey(
    private val source: JcSpringPinnedValueSource,
    private val name: String? = null,
    private val ignoreCase: Boolean = true
) {
    companion object {
        // TODO: Add constructors if necessary
        fun ofSource(source: JcSpringPinnedValueSource, name: String? = null): JcSpringPinnedValueKey = JcSpringPinnedValueKey(source, name, source.caseSensitive())
        fun requestHasBody(): JcSpringPinnedValueKey = ofSource(JcSpringPinnedValueSource.REQUEST_HAS_BODY)
        fun requestAttribute(name: String): JcSpringPinnedValueKey = ofSource(JcSpringPinnedValueSource.REQUEST_ATTRIBUTE, name)
        fun requestPath(): JcSpringPinnedValueKey = ofSource(JcSpringPinnedValueSource.REQUEST_PATH)
        fun requestMethod(): JcSpringPinnedValueKey = ofSource(JcSpringPinnedValueSource.REQUEST_METHOD)
        fun response(): JcSpringPinnedValueKey = ofSource(JcSpringPinnedValueSource.RESPONSE)
        fun requestBody(): JcSpringPinnedValueKey = ofSource(JcSpringPinnedValueSource.REQUEST_BODY)
    }

    override fun hashCode(): Int { 
        val name = if (ignoreCase) name?.uppercase() else name
        return Objects.hash(source, name)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcSpringPinnedValueKey

        if (source != other.source) return false
        if (ignoreCase != other.ignoreCase) return false
        if (!name.equals(other.name, ignoreCase)) return false

        return true
    }

    fun getSource(): JcSpringPinnedValueSource {
        return source
    }

    fun getName(): String? {
        return name
    }

    override fun toString(): String {
        val namePostfix = if (name == null) "" else
            if (ignoreCase) "(${name.uppercase()})" else "($name)"
        return "${source.name} $namePostfix"
    }

}