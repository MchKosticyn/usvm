package org.usvm.api.spring

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.isAssignable
import org.jacodb.impl.cfg.util.isPrimitive
import org.usvm.api.util.Reflection.toJavaClass
import org.usvm.jvm.util.toJavaClass
import org.usvm.jvm.util.toJcClass
import org.usvm.jvm.util.toJcType
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestGetFieldExpression
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestSetFieldStatement
import org.usvm.test.api.UTestStatement
import org.usvm.test.api.UTestStaticMethodCall
import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName

class SpringMockBeanBuilder(
    private val cp: JcClasspath,
    private val testClass: UTestExpression,
    private val reproducing: Boolean = false
) {
    private val mockitoClass = cp.findClassOrNull("org.mockito.Mockito")
    private val whenMethod = mockitoClass?.declaredMethods?.find { it.name == "when" }
    private val ongoingStubbingClass = cp.findClassOrNull("org.mockito.stubbing.OngoingStubbing")
    private val argumentMatchersClass = cp.findClassOrNull("org.mockito.ArgumentMatchers")
    private val thenReturnMethod = ongoingStubbingClass
        ?.declaredMethods
        ?.find { it.name == "thenReturn" && it.parameters.size == 1 }
    private val mockBeanAnnotation = cp.findClassOrNull("org.springframework.boot.test.mock.mockito")
    private val initStatements: MutableList<UTestStatement> = mutableListOf()
    private val mockitoCalls: MutableList<UTestExpression> = mutableListOf()
    private val mockBeanCache = HashMap<JcType, UTestExpression>()

    init {
        check(mockitoClass != null)
        check(argumentMatchersClass != null)
        check(whenMethod != null)
        check(thenReturnMethod != null)
        check(mockBeanAnnotation != null)
    }

    private fun getAnyName(type: TypeName): String {
        // Can all be replaced by Mockito.any(type.class)
        val jcType = type.toJcType(cp)
        check(jcType != null)

        val checkSubtype = { s: KClass<*> -> cp.findType(s.jvmName) == jcType }

        if (checkSubtype(Set::class)) return "Set"
        if (checkSubtype(Map::class)) return "Map"
        if (checkSubtype(List::class)) return "List"
        if (checkSubtype(Collection::class)) return "Collection"
        if (checkSubtype(Iterable::class)) return "Iterable"
        if (checkSubtype(String::class)) return "String"

        if (type.isPrimitive) return type.typeName.replaceFirstChar(Char::titlecase)

        return ""
    }

    private fun getMockitoAnyArguments(method: JcMethod): List<UTestExpression> {
        return method.parameters.map {
            val supportedAnyName = getAnyName(it.type)
            val anyMethod = argumentMatchersClass?.declaredMethods?.find { m -> m.name == "any$supportedAnyName" }
            check(anyMethod != null)
            val args = listOf<UTestExpression>()
            UTestStaticMethodCall(anyMethod, args)
        }
    }

    private fun addMockField(type: JcType, field: JcField, value: UTestExpression): UTestStatement {
        val mockBean = findMockBean(type)
        return UTestSetFieldStatement(
            mockBean,
            field,
            value
        ).also { initStatements.add(it) }
    }

    private fun findReproducingMockBean(type: JcType): UTestExpression {
        val mockBeanField = (testClass.type as JcClassType).fields.find { it.type == type }?.field
        check(mockBeanField != null)
        return UTestGetFieldExpression(
            testClass,
            mockBeanField
        )
    }

    private fun findRenderingMockBean(type: JcType): UTestExpression {
        return UTestAllocateMemoryCall(
            type.toJcClass()!!
        )
    }

    private fun findMockBean(type: JcType): UTestExpression {
        val current = mockBeanCache[type]
        if (current != null)
            return current

        val mockBean = if (reproducing) findReproducingMockBean(type)
        else findRenderingMockBean(type)
        mockBeanCache[type] = mockBean

        return mockBean
    }

    private fun addMockMethod(type: JcType, method: JcMethod, values: List<UTestExpression>): UTestExpression {
        val mockedMethodCallArgs = getMockitoAnyArguments(method)
        val mockedObject = findMockBean(type)

        val mockedMethodCall = UTestMethodCall(
            mockedObject,
            method,
            mockedMethodCallArgs
        )

        val whenCall = UTestStaticMethodCall(
            whenMethod!!,
            listOf(mockedMethodCall)
        )

        return values.fold(whenCall) { call: UTestExpression, result ->
            UTestMethodCall(call, thenReturnMethod!!, listOf(result))
        }.also { mockitoCalls.add(it) }
    }

    fun addMock(mock: JcMockBean): SpringMockBeanBuilder {
        val type = mock.getType()
        mock.getFields().forEach { (field, value) -> addMockField(type, field, value) }
        mock.getMethods().forEach { (method, values) -> addMockMethod(type, method, values) }
        return this
    }

    fun getInitStatements() = initStatements
    fun getMockitoCalls() = mockitoCalls
}