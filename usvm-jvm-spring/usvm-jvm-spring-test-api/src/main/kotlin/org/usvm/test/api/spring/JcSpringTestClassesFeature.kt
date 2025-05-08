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
import org.jacodb.impl.features.classpaths.virtual.JcVirtualClass
import org.jacodb.impl.features.classpaths.virtual.JcVirtualField
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethod
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.jacodb.impl.types.AnnotationValue
import org.jacodb.impl.types.AnnotationValueList
import org.jacodb.impl.types.ClassRef
import org.jacodb.impl.types.EnumRef
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.Opcodes
import org.usvm.jvm.util.typename

class JcSpringTestClassesFeature: JcClasspathExtFeature {

    private lateinit var cp: JcClasspath

    private fun bind(classpath: JcClasspath) {
        cp = classpath
        val mockitoClasses = VirtualMockito.classesIn(cp)
        classes.addAll(mockitoClasses)
    }

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
    companion object {
        private const val MOCK_BEAN_NAME = "org.springframework.boot.test.mock.mockito.MockBean"

        private const val AUTOWIRE_NAME = "org.springframework.beans.factory.annotation.Autowired"

        private const val WEB_MVC_TEST = "org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest"

        private const val COMPONENT_SCAN_FILTER_NAME = "org.springframework.context.annotation.ComponentScan\$Filter"

        private const val FILTER_TYPE_NAME = "org.springframework.context.annotation.FilterType"

        private const val FILTER_ASSIGNABLE_TYPE = "ASSIGNABLE_TYPE"

        private const val WEB_SECURITY_CONFIGURER_NAME =
            "org.springframework.security.config.annotation.web.WebSecurityConfigurer"

        private val securityExcludedConfigurations = listOf(
            "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
            "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
        )


        private val JcClasspath.mockBeanAnnotation: JcAnnotation
            get() {
                val annotationInfo = AnnotationInfo(
                    className = MOCK_BEAN_NAME,
                    visible = true,
                    values = emptyList(),
                    typeRef = null,
                    typePath = null
                )
                return JcAnnotationImpl(annotationInfo, this)
            }

        private val JcClasspath.autowireAnnotation: JcAnnotation
            get() {
                val annotationInfo = AnnotationInfo(
                    className = AUTOWIRE_NAME,
                    visible = true,
                    values = emptyList(),
                    typeRef = null,
                    typePath = null
                )

                return JcAnnotationImpl(annotationInfo, this)
            }
    }

    fun testClassFor(name: String, controller: JcClassOrInterface): JcClassOrInterface {
        val testClassCtor = JcVirtualMethodImpl(
            name = CONSTRUCTOR,
            returnType = TypeNameImpl.fromTypeName(PredefinedPrimitives.Void),
            parameters = emptyList(),
            description = ""
        )

        val testClassIgnoreResultMethod = JcVirtualMethodImpl(
            name = "ignoreResult",
            returnType = TypeNameImpl.fromTypeName(PredefinedPrimitives.Void),
            parameters = listOf(JcVirtualParameter(0, TypeNameImpl.fromTypeName("java.lang.Object"))),
            description = "",
            access = Opcodes.ACC_PUBLIC.or(Opcodes.ACC_STATIC)
        )

        val virtualClass = JcVirtualAnnotatedClass(
            name,
            initialFields = listOf(),
            initialMethods = listOf(testClassCtor, testClassIgnoreResultMethod),
            annotations = listOf(mockMvcAnnotationFor(controller))
        )

        virtualClass.bind(controller.classpath, VirtualLocation())
        classes.add(virtualClass)

        return virtualClass
    }

    private fun addTestClassField(targetClass: JcClassOrInterface, fieldType: JcType, name: String? = null, annotation: JcAnnotation? = null): JcField {
        val fieldName = name ?: (fieldType as JcClassType).jcClass.simpleName.decapitalized
        val annotations = if (annotation != null) listOf(annotation) else emptyList()
        val field = JcVirtualAnnotatedField(
            name = fieldName,
            type = fieldType.getTypename(),
            annotations = annotations,
        )
        field.bind(targetClass)

        return field
    }

    fun addAutowireField(targetClass: JcClassOrInterface, fieldType: JcType, name: String? = null): JcField {
        return addTestClassField(targetClass, fieldType, name, fieldType.classpath.autowireAnnotation)
    }

    fun addMockBeanField(targetClass: JcClassOrInterface, fieldType: JcType, name: String? = null): JcField {
        return addTestClassField(targetClass, fieldType, name, fieldType.classpath.mockBeanAnnotation)
    }

    private val mockMvcSecurityExtraValues: List<Pair<String, AnnotationValue>> get() {

        val filterAnnotation = AnnotationInfo(
            className = COMPONENT_SCAN_FILTER_NAME,
            visible = true,
            values = listOf(
                "type" to EnumRef(FILTER_TYPE_NAME, FILTER_ASSIGNABLE_TYPE),
                "value" to ClassRef(WEB_SECURITY_CONFIGURER_NAME)
            ),
            typeRef = null,
            typePath = null
        )

        val excludeFilters = AnnotationValueList(listOf(filterAnnotation))

        val excludeAutoConfiguration = AnnotationValueList(
            securityExcludedConfigurations.map { className ->
                ClassRef(className)
            }
        )

        val securityExtra = listOf(
            "excludeFilters" to excludeFilters,
            "excludeAutoConfiguration" to excludeAutoConfiguration
        )
        return securityExtra
    }

    private fun mockMvcAnnotationFor(controller: JcClassOrInterface): JcAnnotation {
        val annotationValues = mutableListOf<Pair<String, AnnotationValue>>("value" to ClassRef(controller.typename.typeName))
        val securityEnabled = controller.classpath.findClassOrNull(WEB_SECURITY_CONFIGURER_NAME) != null

        if (securityEnabled) {
            annotationValues.addAll(mockMvcSecurityExtraValues)
        }

        val annotationInfo = AnnotationInfo(
            className = WEB_MVC_TEST,
            visible = true,
            values = annotationValues,
            typeRef = null,
            typePath = null
        )

        return JcAnnotationImpl(annotationInfo, controller.classpath)
    }

    private val classes: MutableSet<JcVirtualClass> = mutableSetOf()

    override fun tryFindClass(classpath: JcClasspath, name: String): JcResolvedClassResult? {
        if (!this::cp.isInitialized) {
            bind(classpath)
        }

        return classes.find { it.name == name }?.let { AbstractJcResolvedResult.JcResolvedClassResultImpl(name, it) }
    }
}

fun getSpringTestClassesFeatureIn(cp: JcClasspath): JcSpringTestClassesFeature {
    val testClassesFeature = cp.features?.filterIsInstance<JcSpringTestClassesFeature>()?.singleOrNull()
    check(testClassesFeature != null) {
        "JcSpringTestClassesFeature required in classpath"
    }
    return testClassesFeature
}
