package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.impl.features.classpaths.VirtualLocation
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClass
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClassImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethod
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.Opcodes

// TODO: implement as ClasspathExtFeature

internal object VirtualMockito {
    fun classesIn(cp: JcClasspath): Set<JcVirtualClass> {
        return setOf(
            mockito(cp),
            ongoingStubbing(cp),
            argumentMatcher(cp)
        )
    }

    private fun mockito(cp: JcClasspath): JcVirtualClass{
        val clazz = JcVirtualClassImpl(
            "org.mockito.Mockito",
            initialFields = listOf(),
            initialMethods = listOf(mockitoWhen)
        )
        clazz.bind(cp, VirtualLocation())
        return clazz
    }

    private val mockitoWhen: JcVirtualMethod by lazy {
        JcVirtualMethodImpl(
            name = "when",
            returnType = TypeNameImpl("org.mockito.stubbing.OngoingStubbing"),
            parameters = listOf(JcVirtualParameter(0, TypeNameImpl("java.lang.Object"))),
            description = "",
            access = Opcodes.ACC_STATIC.or(Opcodes.ACC_PUBLIC)
        )
    }

    private fun ongoingStubbing(cp: JcClasspath): JcVirtualClass {
        val clazz = JcVirtualClassImpl(
            "org.mockito.stubbing.OngoingStubbing",
            initialFields = listOf(),
            initialMethods = listOf(ongoingStubbingThenReturn, ongoingStubbingMultipleThenReturn)
        )
        clazz.bind(cp, VirtualLocation())
        return clazz
    }

    private val ongoingStubbingThenReturn by lazy {
        JcVirtualMethodImpl(
            name = "thenReturn",
            returnType = TypeNameImpl("org.mockito.stubbing.OngoingStubbing"),
            parameters = listOf(JcVirtualParameter(0, TypeNameImpl("java.lang.Object"))),
            description = "",
            access = Opcodes.ACC_PUBLIC
        )
    }

    private val ongoingStubbingMultipleThenReturn by lazy {
        JcVirtualMethodImpl(
            name = "thenReturn",
            returnType = TypeNameImpl("org.mockito.stubbing.OngoingStubbing"),
            parameters = listOf(
                JcVirtualParameter(0, TypeNameImpl("java.lang.Object")),
                JcVirtualParameter(1, TypeNameImpl("java.lang.Object[]"))
            ),
            description = "",
            access = Opcodes.ACC_PUBLIC
        )
    }

    private fun argumentMatcher(cp: JcClasspath): JcVirtualClass {
        val clazz = JcVirtualClassImpl(
            "org.mockito.ArgumentMatchers",
            initialFields = listOf(),
            initialMethods = createMatchers()
        )
        clazz.bind(cp, VirtualLocation())
        return clazz
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
        val suffixes = javaUtilCollectionSuffixes + javaLangCollectionSuffixes + primitiveSuffixes + "" // REQUIRED FOR ANY
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
                    returnType = TypeNameImpl(returnTypeName),
                    parameters = listOf(),
                    description = "",
                    access = Opcodes.ACC_STATIC.or(Opcodes.ACC_PUBLIC)
                )
            )
        }

        return matchers
    }
}
