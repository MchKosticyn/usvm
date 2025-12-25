package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import java.nio.file.Path
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.toType
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestInfo

class JcSpringMvcTestInfo(
    method: JcMethod,
    isExceptional: Boolean,
    testFilePath: Path? = null,
    testPackageName: String? = null,
    testClassName: String? = null,
    testName: String? = null
) : JcSpringUnitTestInfo(method, isExceptional, testFilePath, testPackageName, testClassName, testName) {

    val controller by lazy { method.enclosingClass.toType() }

    override fun hashCode(): Int {
        return method.enclosingClass.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (other == null || other !is JcSpringMvcTestInfo) return false
        return controller == other.controller && testFilePath == other.testFilePath && testClassName == other.testClassName
    }
}
