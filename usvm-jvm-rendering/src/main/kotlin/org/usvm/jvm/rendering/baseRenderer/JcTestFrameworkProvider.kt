package org.usvm.jvm.rendering.baseRenderer

enum class JcRendererTestFramework(val testAnnotationClassName: String, val assertionsClassName: String) {
    JUNIT_5("org.junit.jupiter.api.Test", "org.junit.jupiter.api.Assertions"),
    JUNIT_4("org.junit.Test", ""),
    TEST_NG("org.testng.annotations.Test", "org.testng.Assert")
}

object JcTestFrameworkProvider {
    private var framework: JcRendererTestFramework = JcRendererTestFramework.JUNIT_5

    fun setTestFramework(newFramework: JcRendererTestFramework) {
        framework = newFramework
    }

    val assertionsClassName: String get() = framework.assertionsClassName

    val testAnnotationClassName: String get() = framework.testAnnotationClassName

    val requireActualExpectedEqualsOrder: Boolean get() = framework == JcRendererTestFramework.TEST_NG
}
