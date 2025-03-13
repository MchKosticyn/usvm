package org.usvm.test.internal

import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClassImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethod
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.jacodb.impl.types.TypeNameImpl

object VirtualMockito {
    val mockito by lazy {
        JcVirtualClassImpl(
            "org.mockito.Mockito",
            initialFields = listOf(),
            initialMethods = listOf(mockitoWhen)
        )
    }

    private val mockitoWhen by lazy {
        JcVirtualMethodImpl(
            name = "when",
            returnType = TypeNameImpl.fromTypeName("org.mockito.stubbing.OngoingStubbing"),
            parameters = listOf(JcVirtualParameter(0, TypeNameImpl.fromTypeName("java.lang.Object"))),
            description = ""
        )
    }

    val ongoingStubbing by lazy {
        JcVirtualClassImpl(
            "org.mockito.stubbing.OngoingStubbing",
            initialFields = listOf(),
            initialMethods = listOf(ongoingStubbingThenReturn)
        )
    }

    private val ongoingStubbingThenReturn by lazy {
        JcVirtualMethodImpl(
            name = "thenReturn",
            returnType = TypeNameImpl.fromTypeName("org.mockito.stubbing.OngoingStubbing"),
            parameters = listOf(JcVirtualParameter(0, TypeNameImpl.fromTypeName("java.lang.Object"))),
            description = ""
        )
    }

    val argumentMatcher by lazy {
        JcVirtualClassImpl(
            "org.mockito.ArgumentMatchers",
            initialFields = listOf(),
            initialMethods = listOf()
        )
    }

    fun anyMatcherBy(methodName: String): JcVirtualMethod {
        val suffix = methodName.drop(3)

        val returnTypeName = when {
            javaUtilCollectionSuffixes.contains(suffix) -> "java.util.$suffix"
            javaLangCollectionSuffixes.contains(suffix) -> "java.lang.$suffix"
            PredefinedPrimitives.matches(suffix) -> suffix
            else -> "java.lang.Object"
        }

        val anyMatcher = JcVirtualMethodImpl(
            name = methodName,
            returnType = TypeNameImpl.fromTypeName(returnTypeName),
            parameters = listOf(),
            description = ""
        )
        anyMatcher.bind(argumentMatcher)

        return anyMatcher
    }

    private val javaUtilCollectionSuffixes = listOf("Set", "Map", "List", "Collection")

    private val javaLangCollectionSuffixes = listOf("Iterable", "String")
}