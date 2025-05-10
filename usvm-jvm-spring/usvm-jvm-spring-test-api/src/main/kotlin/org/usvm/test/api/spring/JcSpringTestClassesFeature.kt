package org.usvm.test.api.spring

import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClassOrInterface
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
import org.jacodb.impl.features.classpaths.VirtualLocation
import org.jacodb.impl.features.classpaths.virtual.JcVirtualField
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethod
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.Opcodes

object JcSpringTestClassesFeature: JcClasspathExtFeature {

    const val DEFAULT_TEST_CLASS_NAME = "org.usvm.test.api.spring.TestClass"

    private class JcVirtualAnnotatedField(
        name: String,
        type: TypeName,
        override val annotations: List<JcAnnotation>
    ): JcVirtualFieldImpl(name = name, type = type) {
        override fun bind(clazz: JcClassOrInterface) {
            super.bind(clazz)

            if (clazz is JcVirtualAnnotatedClass) {
                clazz.attachedFields.add(this)
            }
        }
    }

    private class JcVirtualAnnotatedClass(
        name: String,
        initialFields: List<JcVirtualField>,
        initialMethods: List<JcVirtualMethod>,
        override val annotations: List<JcAnnotation>
    ): JcVirtualClassImpl(name = name, initialFields = initialFields, initialMethods = initialMethods) {

        val attachedFields: MutableSet<JcVirtualField> = mutableSetOf()

        override val declaredFields: List<JcVirtualField> get() {
            check(attachedFields.all { it.enclosingClass === this }) {
                "attached field is not bind to the class"
            }
            return super.declaredFields + attachedFields
        }
    }

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
            description = "",
            access = Opcodes.ACC_PUBLIC.or(Opcodes.ACC_STATIC)
        )
    }

    /* TODO: add security disable annotations
        @WebMvcTest(excludeFilters = {@ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = WebSecurityConfigurer.class)},
            excludeAutoConfiguration = {SecurityAutoConfiguration.class,
                                        SecurityFilterAutoConfiguration.class,
                                        OAuth2ClientAutoConfiguration.class,
                                        OAuth2ResourceServerAutoConfiguration.class})
     */

    private lateinit var defaultTestClassClasspath: JcClasspath

    private val defaultTestClass by lazy {
        val virtualClass = JcVirtualAnnotatedClass(
            DEFAULT_TEST_CLASS_NAME,
            initialFields = listOf(),
            initialMethods = listOf(testClassCtor, testClassIgnoreResultMethod),
            annotations = emptyList()
        )
        virtualClass.bind(defaultTestClassClasspath, VirtualLocation())
        virtualClass
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
        if (!this::defaultTestClassClasspath.isInitialized) {
            defaultTestClassClasspath = classpath
            VirtualMockito.useIn(classpath)
        }

        return classes.find { it.name == name }?.let { AbstractJcResolvedResult.JcResolvedClassResultImpl(name, it) }
    }
}
