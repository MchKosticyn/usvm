package org.usvm.jvm.rendering.unsafeRenderer

object ReflectionUtilName {
    const val SPRING = "org.springframework.test.util.ReflectionTestUtils"
    const val USVM = "org.usvm.jvm.rendering.ReflectionUtils"
    const val USVM_SIMPLE = "ReflectionUtils"

    fun isValidFullName(name: String) = name in listOf(SPRING, USVM)
}