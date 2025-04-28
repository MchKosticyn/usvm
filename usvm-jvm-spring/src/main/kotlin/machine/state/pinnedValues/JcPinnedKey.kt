package machine.state.pinnedValues

import org.jacodb.api.jvm.JcMethod
import java.util.*

abstract class JcPinnedKey(
    private val source: JcSpringPinnedValueSource
) {
    companion object {
        // TODO: Add constructors if necessary
        fun ofSource(source: JcSpringPinnedValueSource): JcPinnedKey = JcSimplePinnedKey(source)
        fun ofName(source: JcSpringPinnedValueSource, name: String) = JcStringPinnedKey(source, name, source.caseSensitive())
        fun requestHasBody(): JcSimplePinnedKey = JcSimplePinnedKey(JcSpringPinnedValueSource.REQUEST_HAS_BODY)
        fun requestAttribute(name: String): JcStringPinnedKey = JcStringPinnedKey(JcSpringPinnedValueSource.REQUEST_ATTRIBUTE, name)
        fun requestPath(): JcSimplePinnedKey = JcSimplePinnedKey(JcSpringPinnedValueSource.REQUEST_PATH)
        fun requestMethod(): JcSimplePinnedKey = JcSimplePinnedKey(JcSpringPinnedValueSource.REQUEST_METHOD)
        fun responseStatus(): JcSimplePinnedKey = JcSimplePinnedKey(JcSpringPinnedValueSource.RESPONSE_STATUS)
        fun responseContent(): JcSimplePinnedKey = JcSimplePinnedKey(JcSpringPinnedValueSource.RESPONSE_CONTENT)
        fun requestBody(): JcSimplePinnedKey = JcSimplePinnedKey(JcSpringPinnedValueSource.REQUEST_BODY)
        fun requestContentType(): JcSimplePinnedKey = JcSimplePinnedKey(JcSpringPinnedValueSource.REQUEST_CONTENT_TYPE)
        fun mockCallResult(method: JcMethod) = JcObjectPinnedKey(JcSpringPinnedValueSource.MOCK_RESULT, method)
    }

    fun getSource() = source
}

class JcSimplePinnedKey(
    private val source: JcSpringPinnedValueSource,
) : JcPinnedKey(source) {

    override fun hashCode(): Int {
        return Objects.hash(getSource())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as JcSimplePinnedKey
        return getSource() == other.getSource()
    }

    override fun toString(): String {
        return source.name
    }
}

class JcObjectPinnedKey<T>(
    private val source: JcSpringPinnedValueSource,
    private val obj: T? = null,
) : JcPinnedKey(source) {

    override fun hashCode(): Int {
        return Objects.hash(getSource(), obj)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as JcObjectPinnedKey<*>
        return getSource() == other.getSource() && getObj() == other.getObj()
    }

    fun getObj() = obj

    override fun toString(): String {
        return "${source.name} ($obj)"
    }
}

class JcStringPinnedKey(
    private val source: JcSpringPinnedValueSource,
    private val name: String,
    private val ignoreCase: Boolean = true
) : JcPinnedKey(source) {

    override fun hashCode(): Int {
        val name = if (ignoreCase) name.uppercase() else name
        return Objects.hash(source, name)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JcStringPinnedKey

        if (source != other.source) return false
        if (ignoreCase != other.ignoreCase) return false
        if (!name.equals(other.name, ignoreCase)) return false

        return true
    }

    fun getName() = name

    override fun toString(): String {
        val namePostfix = if (ignoreCase) "(${name.uppercase()})" else "($name)"
        return "${source.name} $namePostfix"
    }
}
