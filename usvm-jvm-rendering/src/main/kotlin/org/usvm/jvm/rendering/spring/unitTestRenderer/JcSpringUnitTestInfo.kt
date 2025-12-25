package org.usvm.jvm.rendering.spring.unitTestRenderer

import java.nio.file.Path
import org.jacodb.api.jvm.JcMethod
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestInfo

open class JcSpringUnitTestInfo(
    method: JcMethod,
    isExceptional: Boolean,
    testFilePath: Path? = null,
    testPackageName: String? = null,
    testClassName: String? = null,
    testName: String? = null
) : JcUnsafeTestInfo(method, isExceptional, testFilePath, testPackageName, testClassName, testName)