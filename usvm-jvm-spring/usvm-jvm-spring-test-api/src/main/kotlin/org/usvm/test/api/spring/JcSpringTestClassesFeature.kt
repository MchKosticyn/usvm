package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathExtFeature
import org.jacodb.api.jvm.JcClasspathExtFeature.JcResolvedClassResult
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.TypeName
import org.jacodb.impl.bytecode.JcAnnotationImpl
import org.jacodb.impl.features.classpaths.AbstractJcResolvedResult
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClassImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualFieldImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.types.AnnotationInfo
import org.usvm.jvm.util.getTypename
import org.jacodb.api.jvm.ext.CONSTRUCTOR
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.jacodb.impl.types.TypeNameImpl

object JcSpringTestClassesFeature: JcClasspathExtFeature {

    const val DEFAULT_TEST_CLASS_NAME = "org.usvm.test.api.spring.TestClass"

    private class JcVirtualAnnotatedField(
        name: String,
        type: TypeName,
        override val annotations: List<JcAnnotation>
    ): JcVirtualFieldImpl(name = name, type = type)

    private const val MOCK_BEAN_NAME = "org.springframework.boot.test.mock.mockito.MockBean"

    private const val AUTOWIRE_NAME = "org.springframework.beans.factory.annotation.Autowired"

    private val JcClasspath.mockBeanAnnotation: JcAnnotation get() {
        val annotationInfo = AnnotationInfo(
            className = MOCK_BEAN_NAME,
            visible = true,
            values = emptyList(),
            typeRef = null,
            typePath = null
        )
        return JcAnnotationImpl(annotationInfo, this)
    }

    private val JcClasspath.autowireAnnotation: JcAnnotation get() {
        val annotationInfo = AnnotationInfo(
            className = AUTOWIRE_NAME,
            visible = true,
            values = emptyList(),
            typeRef = null,
            typePath = null
        )
        return JcAnnotationImpl(annotationInfo, this)
    }

    private val testClassCtor by lazy {
        JcVirtualMethodImpl(
            name = CONSTRUCTOR,
            returnType = TypeNameImpl.fromTypeName(PredefinedPrimitives.Void),
            parameters = emptyList(),
            description = ""
        )
    }

    private val testClassIgnoreResultMethod by lazy {
        JcVirtualMethodImpl(
            name = "ignoreResult",
            returnType = TypeNameImpl.fromTypeName(PredefinedPrimitives.Void),
            parameters = listOf(JcVirtualParameter(0, TypeNameImpl.fromTypeName("java.lang.Object"))),
            description = ""
        )
    }

    private val defaultTestClass by lazy {
        JcVirtualClassImpl(
            DEFAULT_TEST_CLASS_NAME,
            initialFields = listOf(),
            initialMethods = listOf(testClassCtor, testClassIgnoreResultMethod)
        )
    }

    private fun addTestClassField(fieldType: JcType, name: String? = null, annotation: JcAnnotation? = null): JcField {
        val fieldName = name ?: (fieldType as JcClassType).jcClass.simpleName.decapitalized
        val annotations = if (annotation != null) listOf(annotation) else emptyList()
        val field = JcVirtualAnnotatedField(
            name = fieldName,
            type = fieldType.getTypename(),
            annotations = annotations,
        )
        field.bind(defaultTestClass)

        return field
    }

    fun addAutowireField(fieldType: JcType, name: String? = null): JcField {
        return addTestClassField(fieldType, name, fieldType.classpath.autowireAnnotation)
    }

    fun addMockBeanField(fieldType: JcType, name: String? = null): JcField {
        return addTestClassField(fieldType, name, fieldType.classpath.mockBeanAnnotation)
    }

    private val classes by lazy {
        setOf(
            VirtualMockito.mockito,
            VirtualMockito.ongoingStubbing,
            VirtualMockito.argumentMatcher,
            defaultTestClass
        )
    }

    override fun tryFindClass(classpath: JcClasspath, name: String): JcResolvedClassResult? {
        return classes.find { it.name == name }?.let { AbstractJcResolvedResult.JcResolvedClassResultImpl(name, it) }
    }
}
