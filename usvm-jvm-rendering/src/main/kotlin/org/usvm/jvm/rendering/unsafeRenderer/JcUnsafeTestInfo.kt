package org.usvm.jvm.rendering.unsafeRenderer

import java.nio.file.Path
import org.jacodb.api.jvm.JcMethod
import org.usvm.jvm.rendering.testRenderer.JcTestInfo

open class JcUnsafeTestInfo(
    method: JcMethod,
    isExceptional: Boolean,
    testFilePath: Path? = null,
    testClassName: String? = null,
    testName: String? = null
) : JcTestInfo(method, isExceptional, testFilePath, testClassName, testName)