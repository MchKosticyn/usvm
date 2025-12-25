package org.usvm.jvm.rendering.testRenderer

import java.nio.file.Path
import org.jacodb.api.jvm.JcMethod
import org.usvm.jvm.rendering.normalized

abstract class JcTestInfo(
    val method: JcMethod,
    val isExceptional: Boolean? = null,
    val testFilePath: Path?,
    val testPackageName: String?,
    val testClassName: String?,
    val testName: String?
) {
    private val defaultNamePrefix: String get() = "${method.name}$isExceptionalSuffix".normalized

    val testNamePrefix: String get() = testName ?: defaultNamePrefix

    private val isExceptionalSuffix: String
        get() = when (isExceptional) {
            true -> "Exceptional"
            else -> ""
        }

    override fun hashCode(): Int {
        return method.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JcTestInfo) return false
        return method == other.method && testFilePath == other.testFilePath && testClassName == other.testClassName
    }
}
