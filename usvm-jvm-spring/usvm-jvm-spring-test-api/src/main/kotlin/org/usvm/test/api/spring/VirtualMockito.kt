package org.usvm.test.api.spring

import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClassImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethod
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.jacodb.impl.types.TypeNameImpl

// TODO: implement as ClasspathExtFeature

internal object VirtualMockito {
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
            initialMethods = createMatchers()
        )
    }

    private val javaUtilCollectionSuffixes = listOf("Set", "Map", "List", "Collection")

    private val javaLangCollectionSuffixes = listOf("Iterable", "String")

    private fun createMatchers(): List<JcVirtualMethod> {
        val primitiveSuffixes = listOf(
            PredefinedPrimitives.Boolean,
            PredefinedPrimitives.Byte,
            PredefinedPrimitives.Char,
            PredefinedPrimitives.Double,
            PredefinedPrimitives.Float,
            PredefinedPrimitives.Int,
            PredefinedPrimitives.Long,
            PredefinedPrimitives.Short
        )
        val suffixes = javaUtilCollectionSuffixes + javaLangCollectionSuffixes + primitiveSuffixes
        val matchers = mutableListOf<JcVirtualMethod>()
        for (suffix in suffixes.distinct()) {
            val returnTypeName = when {
                javaUtilCollectionSuffixes.contains(suffix) -> "java.util.$suffix"
                javaLangCollectionSuffixes.contains(suffix) -> "java.lang.$suffix"
                PredefinedPrimitives.matches(suffix) -> suffix
                else -> "java.lang.Object"
            }

            matchers.add(
                JcVirtualMethodImpl(
                    name = "any${suffix.replaceFirstChar(Char::titlecase)}",
                    returnType = TypeNameImpl.fromTypeName(returnTypeName),
                    parameters = listOf(),
                    description = ""
                )
            )
        }

        return matchers
    }
}
