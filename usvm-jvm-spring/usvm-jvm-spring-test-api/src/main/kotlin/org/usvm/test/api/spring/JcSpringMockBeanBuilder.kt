package org.usvm.test.api.spring

import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcParameter
import org.jacodb.api.jvm.JcRefType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.ext.findType
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestGetFieldExpression
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestSetFieldStatement
import org.usvm.test.api.UTestStatement
import org.usvm.test.api.UTestStaticMethodCall

class SpringMockBeanBuilder(
    private val cp: JcClasspath,
    private val testClass: UTestExpression?,
    private val reproducing: Boolean = false
) {
    private val mockitoClass = cp.findClassOrNull("org.mockito.Mockito")
        ?: VirtualMockito.mockito
    private val whenMethod = mockitoClass.declaredMethods.find { it.name == "when" }

    private val ongoingStubbingClass = cp.findClassOrNull("org.mockito.stubbing.OngoingStubbing")
        ?: VirtualMockito.ongoingStubbing

    private val thenReturnMethod = ongoingStubbingClass.declaredMethods.find { it.name == "thenReturn" && it.parameters.size == 1 }

    private val argumentMatchersClass = cp.findClassOrNull("org.mockito.ArgumentMatchers")
        ?: VirtualMockito.argumentMatcher

    private val initStatements: MutableList<UTestStatement> = mutableListOf()
    private val mockitoCalls: MutableList<UTestExpression> = mutableListOf()
    private val mockBeanCache = HashMap<JcType, UTestExpression>()

    init {
        check(whenMethod != null)
        check(thenReturnMethod != null)
    }

    private fun resolveAnyMatcherName(param: JcParameter): String {
        val paramTypeName = param.type.typeName
        val jcType = cp.findType(paramTypeName)
        val checkTypeEqualTo = { s: KClass<*> -> cp.findTypeOrNull(s.jvmName) == jcType }

        val anyMatcherSuffix = when {
            checkTypeEqualTo(Set::class) -> "Set"
            checkTypeEqualTo(Map::class) -> "Map"
            checkTypeEqualTo(List::class) -> "List"
            checkTypeEqualTo(Collection::class) -> "Collection"
            checkTypeEqualTo(Iterable::class) -> "Iterable"
            checkTypeEqualTo(String::class) -> "String"
            PredefinedPrimitives.matches(paramTypeName) -> paramTypeName.replaceFirstChar(Char::titlecase)
            else -> ""
        }

        return "any$anyMatcherSuffix"
    }

    private fun mockitoAnyMatcherFor(param: JcParameter): JcMethod {
        val mockitoAnyMethodName = resolveAnyMatcherName(param)
        val anyMethod = argumentMatchersClass.declaredMethods.find { m -> m.name == mockitoAnyMethodName }
            ?: VirtualMockito.anyMatcherBy(mockitoAnyMethodName)
        return anyMethod
    }

    private fun anyMatchersForParametersOf(method: JcMethod): List<UTestExpression> {
        return method.parameters.map {
            val anyMethod = mockitoAnyMatcherFor(it)
            val args = listOf<UTestExpression>()
            UTestStaticMethodCall(anyMethod, args)
        }
    }

    private fun addMockField(type: JcType, field: JcField, value: UTestExpression): UTestStatement {
        val mockBean = findMockBean(type)
        val setFieldStatement = UTestSetFieldStatement(mockBean, field, value)
        initStatements.add(setFieldStatement)
        return setFieldStatement
    }

    private fun findReproducingMockBean(type: JcType): UTestExpression {
        check(testClass != null) { "allowed only for rendering" }

        val mockBeanField = (testClass.type as JcClassType).fields.find { it.type == type }?.field
        check(mockBeanField != null)

        return UTestGetFieldExpression(
            testClass,
            mockBeanField
        )
    }

    private fun findRenderingMockBean(type: JcType): UTestExpression {
        check(testClass == null) { "no test class expected" }

        return UTestAllocateMemoryCall((type as JcRefType).jcClass)
    }

    private fun findMockBean(type: JcType): UTestExpression {
        val current = mockBeanCache[type]
        if (current != null)
            return current

        val mockBean =
            if (reproducing) findReproducingMockBean(type)
            else findRenderingMockBean(type)
        mockBeanCache[type] = mockBean

        return mockBean
    }

    private fun addMockMethod(type: JcType, method: JcMethod, values: List<UTestExpression>): UTestExpression {
        check (!method.isStatic) { "no static method mocks expected in mockBean" }

        val mockedMethodCallArgs = anyMatchersForParametersOf(method)
        val mockedObject = findMockBean(type)

        val mockedMethodCall = UTestMethodCall(
            mockedObject,
            method,
            mockedMethodCallArgs
        )

        check(whenMethod != null) { "virtual method should be used, if mockito not in classpath" }

        val whenCall = UTestStaticMethodCall(
            whenMethod,
            listOf(mockedMethodCall)
        )

        check(thenReturnMethod != null) { "virtual method should be used, if mockito not in classpath" }

        val mockitoCall = values.fold(whenCall) { call: UTestExpression, result ->
            UTestMethodCall(call, thenReturnMethod, listOf(result))
        }
        mockitoCalls.add(mockitoCall)

        return mockitoCall
    }

    fun addMock(mock: JcSpringMockBean): SpringMockBeanBuilder {
        val type = mock.getType()
        mock.getFields().forEach { (field, value) -> addMockField(type, field, value) }
        mock.getMethods().forEach { (method, values) -> addMockMethod(type, method, values) }
        return this
    }

    fun getInitStatements() = initStatements
    fun getMockitoCalls() = mockitoCalls
}
