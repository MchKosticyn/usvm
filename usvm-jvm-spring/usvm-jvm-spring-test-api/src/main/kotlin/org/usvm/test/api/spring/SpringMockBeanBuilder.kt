package org.usvm.test.api.spring

import kotlin.reflect.KClass
import kotlin.reflect.jvm.jvmName
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcParameter
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.usvm.test.api.UTestArraySetStatement
import org.usvm.test.api.UTestCreateArrayExpression
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestGetFieldExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestIntExpression
import org.usvm.test.api.UTestMethodCall
import org.usvm.test.api.UTestMockObject
import org.usvm.test.api.UTestSetFieldStatement
import org.usvm.test.api.UTestStatement
import org.usvm.test.api.UTestStaticMethodCall

class SpringMockBeanBuilder(
    private val cp: JcClasspath,
    private val testClass: UTestExpression
) {
    private val mockitoClass = cp.findClassOrNull("org.mockito.Mockito")
        ?: error("mockito class not found")

    private val whenMethod = mockitoClass.declaredMethods.find { it.name == "when" }
        ?: error("mockito when method not found")

    private val ongoingStubbingClass = cp.findClassOrNull("org.mockito.stubbing.OngoingStubbing")
        ?: error("mockito ongoingStubbing not found")

    private val thenReturnMethod = ongoingStubbingClass.declaredMethods.find {
        it.name == "thenReturn" && it.parameters.size == 1
    } ?: error("mockito thenReturn method not found")

    private val thenReturnMultipleMethod = ongoingStubbingClass.declaredMethods.find {
        it.name == "thenReturn" && it.parameters.size == 2
    } ?: error("mockito thenReturn method not found")

    private val argumentMatchersClass = cp.findClassOrNull("org.mockito.ArgumentMatchers")
        ?: error("mockito argumentMatchers class not found")

    private val initStatements: MutableList<UTestInst> = mutableListOf()
    private val mockitoCalls: MutableList<UTestExpression> = mutableListOf()
    private val mockBeanCache = HashMap<JcType, UTestExpression>()

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
            ?: error("mockitoAnyMethod $mockitoAnyMethodName not found")
        return anyMethod
    }

    private fun anyMatchersForParametersOf(method: JcMethod): List<UTestExpression> {
        return method.parameters.map {
            val anyMethod = mockitoAnyMatcherFor(it)
            val args = listOf<UTestExpression>()
            UTestStaticMethodCall(anyMethod, args)
        }
    }

    private fun addMockField(
        mockBean: UTestExpression,
        field: JcField,
        value: UTestExpression
    ): UTestStatement {
        val setFieldStatement = UTestSetFieldStatement(mockBean, field, value)
        initStatements.add(setFieldStatement)
        return setFieldStatement
    }

    private fun findMockBean(type: JcType): UTestExpression {
        val current = mockBeanCache[type]
        if (current != null)
            return current

        val testClassType = (testClass.type as JcClassType).jcClass

        val mockBeanField = testClassType.declaredFields.find { it.type.typeName == type.typeName }
            ?: getSpringTestClassesFeatureIn(cp).addMockBeanField(testClassType, type)

        check(testClassType.declaredFields.any { it.type.typeName == type.typeName }) {
            "mockBean field should be declared in test class"
        }

        val mockBean = UTestGetFieldExpression(
            testClass,
            mockBeanField
        )
        mockBeanCache[type] = mockBean

        return mockBean
    }

    private fun addMockMethod(
        mockBean: UTestExpression,
        method: JcMethod,
        values: List<UTestExpression>
    ): UTestExpression {
        check (!method.isStatic) { "no static method mocks expected in mockBean" }

        val mockedMethodCallArgs = anyMatchersForParametersOf(method)

        val mockedMethodCall = UTestMethodCall(
            mockBean,
            method,
            mockedMethodCallArgs
        )

        val whenCall = UTestStaticMethodCall(
            whenMethod,
            listOf(mockedMethodCall)
        )

        if (values.size == 1) {
            val mockitoCall = UTestMethodCall(whenCall, thenReturnMethod, listOf(values.single()))
            mockitoCalls.add(mockitoCall)
            return mockitoCall
        }

        val head = values.first()
        val tail = values.drop(1)
        val tailSize = UTestIntExpression(tail.size, cp.int)
        val tailArray = UTestCreateArrayExpression(head.type!!, tailSize)
        val assignInstructions = List(tail.size) {
            UTestArraySetStatement(tailArray, UTestIntExpression(it, cp.int), tail[it])
        }
        initStatements.addAll(assignInstructions)
        val mockitoCall = UTestMethodCall(whenCall, thenReturnMultipleMethod, listOf(head, tailArray))
        mockitoCalls.add(mockitoCall)
        return mockitoCall
    }

    fun addMockBean(mock: UTestMockObject): SpringMockBeanBuilder {
        val mockBean = findMockBean(mock.type)
        mock.fields.forEach { (field, value) -> addMockField(mockBean, field, value) }
        mock.methods.forEach { (method, values) -> addMockMethod(mockBean, method, values) }

        return this
    }

    fun getInitStatements() = initStatements
    fun getMockitoCalls() = mockitoCalls
}
