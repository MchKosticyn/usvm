package utils

import features.JcLambdaFeature
import machine.JcConcreteMemoryClassLoader
import org.jacodb.api.jvm.ClassSource
import org.jacodb.api.jvm.JcArrayType
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedField
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcRawCallInst
import org.jacodb.api.jvm.cfg.JcRawStaticCallExpr
import org.jacodb.api.jvm.ext.allSuperHierarchy
import org.jacodb.api.jvm.ext.findFieldOrNull
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.isEnum
import org.jacodb.api.jvm.ext.packageName
import org.jacodb.api.jvm.ext.toType
import org.jacodb.api.jvm.throwClassNotFound
import org.jacodb.approximation.Approximations
import org.jacodb.approximation.JcEnrichedVirtualField
import org.jacodb.approximation.JcEnrichedVirtualMethod
import org.jacodb.approximation.OriginalClassName
import org.jacodb.impl.features.classpaths.JcUnknownType
import org.jacodb.impl.types.TypeNameImpl
import org.usvm.api.util.Reflection.allocateInstance
import org.usvm.api.util.Reflection.toJavaExecutable
import org.usvm.concrete.api.internal.InitHelper
import org.usvm.jvm.util.getFieldValue as getFieldValueUnsafe
import org.usvm.jvm.util.setFieldValue as setFieldValueUnsafe
import org.usvm.machine.JcContext
import org.usvm.util.allFields
import org.usvm.util.allInstanceFields
import org.usvm.util.javaName
import org.usvm.util.name
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import java.lang.reflect.Proxy
import java.nio.ByteBuffer

internal val Class<*>.safeDeclaredFields: List<Field>
    get() {
        return try {
            declaredFields.toList()
        } catch (e: Throwable) {
            emptyList()
        }
    }

// TODO: cache?
@Suppress("RecursivePropertyAccessor")
internal val Class<*>.allFields: List<Field>
    get() = safeDeclaredFields + (superclass?.allFields ?: emptyList())

internal val JcClassType.declaredInstanceFields: List<JcTypedField>
    get() = declaredFields.filter { !it.isStatic }

internal val Class<*>.allInstanceFields: List<Field>
    get() = allFields.filter { !Modifier.isStatic(it.modifiers) }

internal val JcClassOrInterface.staticFields: List<JcField>
    get() = declaredFields.filter { it.isStatic }

internal val Class<*>.staticFields: List<Field>
    get() = safeDeclaredFields.filter { Modifier.isStatic(it.modifiers) }

internal val Field.isStatic: Boolean
    get() = Modifier.isStatic(modifiers)

internal fun Field.getFieldValue(obj: Any): Any? {
    check(!isStatic)
    check(this.declaringClass.isAssignableFrom(obj.javaClass)) {
        "field $this cannot be red from object of ${obj.javaClass}"
    }

    return try {
        isAccessible = true
        get(obj)
    } catch (_: Throwable) {
        getFieldValueUnsafe(obj)
    }
}

private val forbiddenModificationClasses = setOf<Class<*>>(
    java.lang.Class::class.java,
    java.lang.reflect.Field::class.java,
    java.lang.reflect.Method::class.java,
    java.lang.Thread::class.java,
    java.lang.String::class.java,
    java.lang.Integer::class.java,
    java.lang.Long::class.java,
    java.lang.Float::class.java,
    java.lang.Double::class.java,
    java.lang.Boolean::class.java,
    java.lang.Byte::class.java,
    java.lang.Short::class.java,
    java.lang.Character::class.java,
    java.lang.Void::class.java,
)

private val Class<*>.isForbiddenToModify: Boolean
    get() = forbiddenModificationClasses.any { it.isAssignableFrom(this) }

class ForbiddenModificationException(msg: String) : Exception(msg)

internal fun Field.setFieldValue(obj: Any, value: Any?) {
    check(!isStatic)
    check(declaringClass.isAssignableFrom(obj.javaClass)) {
        "field $this cannot be written to object of ${obj.javaClass}"
    }

    if (declaringClass.isForbiddenToModify)
        throw ForbiddenModificationException(declaringClass.name)

    try {
        isAccessible = true
        set(obj, value)
    } catch (_: Throwable) {
        setFieldValueUnsafe(obj, value)
    }
}

internal fun Field.getStaticFieldValue(): Any? {
    check(isStatic)
    return try {
        isAccessible = true
        get(null)
    } catch (_: Throwable) {
        getFieldValueUnsafe(null)
    }
}

internal fun Field.setStaticFieldValue(value: Any?) {
    check(isStatic)
    try {
        isAccessible = true
        set(null, value)
    } catch (_: Throwable) {
        setFieldValueUnsafe(null, value)
    }
}

internal val Field.isFinal: Boolean
    get() = Modifier.isFinal(modifiers)

@Suppress("UNCHECKED_CAST")
internal fun <Value> Any.getArrayValue(index: Int): Value {
    return when (this) {
        is IntArray -> this[index] as Value
        is ByteArray -> this[index] as Value
        is CharArray -> this[index] as Value
        is LongArray -> this[index] as Value
        is FloatArray -> this[index] as Value
        is ShortArray -> this[index] as Value
        is DoubleArray -> this[index] as Value
        is BooleanArray -> this[index] as Value
        is Array<*> -> this[index] as Value
        else -> error("getArrayValue: unexpected array $this")
    }
}

@Suppress("UNCHECKED_CAST")
internal fun <Value> Any.setArrayValue(index: Int, value: Value) {
    when (this) {
        is IntArray -> this[index] = value as Int
        is ByteArray -> this[index] = value as Byte
        is CharArray -> this[index] = value as Char
        is LongArray -> this[index] = value as Long
        is FloatArray -> this[index] = value as Float
        is ShortArray -> this[index] = value as Short
        is DoubleArray -> this[index] = value as Double
        is BooleanArray -> this[index] = value as Boolean
        is Array<*> -> (this as Array<Value>)[index] = value
        else -> error("setArrayValue: unexpected array $this")
    }
}

internal val JcField.toJavaField: Field?
    get() {
        try {
            val type = JcConcreteMemoryClassLoader.loadClass(enclosingClass)
            val fields = if (isStatic) type.staticFields else type.allInstanceFields
            val field = fields.find { it.name == name }
            check(field == null || field.type.typeName == this.type.typeName) {
                "invalid field: types of field $field and $this differ ${field?.type?.typeName} and ${this.type.typeName}"
            }
            return field
        } catch (e: Throwable) {
            return null
        }
    }

internal val JcMethod.toJavaMethod: Executable?
    get() = this.toJavaExecutable(JcConcreteMemoryClassLoader)

val JcMethod.toTypedMethod: JcTypedMethod
    get() = enclosingClass.toType().declaredMethods.find { this == it.method }!!

internal val JcEnrichedVirtualMethod.approximationMethod: JcMethod?
    get() {
        val originalClassName = OriginalClassName(enclosingClass.name)
        val approximationClassName =
            Approximations.findApproximationByOriginOrNull(originalClassName)
                ?: return null
        return enclosingClass.classpath.findClassOrNull(approximationClassName)
            ?.declaredMethods
            ?.find { it.name == this.name }
    }

internal val JcType.isInstanceApproximation: Boolean
    get() {
        if (this !is JcClassType)
            return false

        // TODO: check field or method exists in bytecode via classNode
        val originalType = JcConcreteMemoryClassLoader.loadClass(jcClass)
        val originalFieldNames = originalType.allInstanceFields.map { it.name }
        return this.allInstanceFields.any {
            it.field is JcEnrichedVirtualField
                    && !originalFieldNames.contains(it.field.name)
        }
    }

internal val JcType.isStaticApproximation: Boolean
    get() {
        if (this !is JcClassType)
            return false

        val originalType = JcConcreteMemoryClassLoader.loadClass(jcClass)
        val originalFieldNames = originalType.staticFields.map { it.name }
        return this.jcClass.staticFields.any { !originalFieldNames.contains(it.name) }
    }

@Suppress("RecursivePropertyAccessor")
internal val JcType.isEnum: Boolean
    get() = this is JcClassType && (this.jcClass.isEnum || this.superType?.isEnum == true)

internal val JcType.isEnumArray: Boolean
    get() = this is JcArrayType && this.elementType.let { it is JcClassType && it.jcClass.isEnum }

internal val JcType.internalName: String
    get() = if (this is JcClassType) this.name else this.typeName

internal val Class<*>.isProxy: Boolean
    get() = Proxy.isProxyClass(this)

internal val String.isLambdaTypeName: Boolean
    get() = contains("\$\$Lambda\$")

internal fun getLambdaCanonicalTypeName(typeName: String): String {
    check(typeName.isLambdaTypeName)
    return typeName.split('/')[0]
}

internal val Class<*>.isLambda: Boolean
    get() = typeName.isLambdaTypeName

internal val Class<*>.isThreadLocal: Boolean
    get() = ThreadLocal::class.java.isAssignableFrom(this)

internal val JcClassOrInterface.isThreadLocal: Boolean
    get() = allSuperHierarchyWithThis.any { it.name == "java.lang.ThreadLocal" }

internal val Class<*>.isByteBuffer: Boolean
    get() = ByteBuffer::class.java.isAssignableFrom(this)

internal val Class<*>.hasStatics: Boolean
    get() = staticFields.isNotEmpty()

internal val JcClassOrInterface.isLambda: Boolean
    get() = name.contains("\$\$Lambda\$")

internal val JcClassOrInterface.isException: Boolean
    get() = allSuperHierarchyWithThis.any { it.name == "java.lang.Throwable" }

internal val JcMethod.isExceptionCtor: Boolean
    get() = isConstructor && enclosingClass.isException

internal val JcMethod.isInstrumentedClinit: Boolean
    get() = isClassInitializer && rawInstList.any {
        it is JcRawCallInst && it.callExpr is JcRawStaticCallExpr
                && it.callExpr.methodName == InitHelper::afterClinit.javaName
    }

internal val JcMethod.isInstrumentedInit: Boolean
    get() = isConstructor && rawInstList.any {
        it is JcRawCallInst && it.callExpr is JcRawStaticCallExpr
                && it.callExpr.methodName == InitHelper::afterInit.javaName
    }

// TODO: cache?
internal val Class<*>.notTracked: Boolean
    get() = this.isPrimitive || this.isEnum || isImmutable

internal val Class<*>.notTrackedWithSubtypes: Boolean
    get() = this.isPrimitive || this.isEnum || isImmutableWithSubtypes

internal val JcClassOrInterface.notTracked: Boolean
    get() = this.isEnum || isImmutable

private val immutableTypes = setOf<Class<*>>(
    java.lang.String::class.java,
    java.lang.Integer::class.java,
    java.lang.Long::class.java,
    java.lang.Float::class.java,
    java.lang.Double::class.java,
    java.lang.Boolean::class.java,
    java.lang.Byte::class.java,
    java.lang.Short::class.java,
    java.lang.Character::class.java,
    java.lang.StackTraceElement::class.java,
    java.lang.Void::class.java,
    java.lang.System::class.java,
    java.lang.Math::class.java,
    java.lang.reflect.Array::class.java,
    java.lang.Class::class.java,
    java.lang.Thread::class.java,
    java.lang.Process::class.java,
    java.math.BigInteger::class.java,
    java.math.BigDecimal::class.java,

    java.io.File::class.java,

    java.awt.Color::class.java,
    java.awt.Font::class.java,
    java.awt.BasicStroke::class.java,
    java.awt.Paint::class.java,
    java.awt.GradientPaint::class.java,
    java.awt.LinearGradientPaint::class.java,
    java.awt.RadialGradientPaint::class.java,
    java.awt.Cursor::class.java,

    java.security.Permission::class.java,

    java.util.Locale::class.java,
    java.util.UUID::class.java,
    java.util.Collections::class.java,
    java.util.Arrays::class.java,
    java.util.Timer::class.java,

    java.net.URL::class.java,
    java.net.URI::class.java,
    java.net.Inet4Address::class.java,
    java.net.Inet6Address::class.java,
    java.net.InetSocketAddress::class.java,
    java.net.NetPermission::class.java,
)

// TODO: make whitelist of mutable types instead of blacklist of immutable (check all packages of corretto-17) #Valya
private val packagesWithImmutableTypes = setOf(
    "java.lang.reflect",
    "java.lang.invoke",
    "java.lang.ref",
    "java.lang.annotation",
    "java.lang.constant",
    "java.lang.module",
    "java.lang.runtime",
    "java.time",
    "sun.reflect",
    "sun.instrument",
    "org.mockito.internal",
    "java.util.zip",
)

internal val Class<*>.isClassLoader: Boolean
    get() = ClassLoader::class.java.isAssignableFrom(this)

private fun typeNameIsInternal(name: String): Boolean {
    return name.startsWith("org.usvm.") && !name.startsWith("org.usvm.samples") ||
            name.startsWith("runtime.LibSLRuntime") ||
            name.startsWith("runtime.LibSLGlobals") ||
            name.startsWith("generated.") ||
            name.startsWith("stub.") ||
            name.startsWith("org.jacodb.")
}

internal val Class<*>.isInternalType: Boolean
    get() = typeNameIsInternal(name)

internal val JcClassOrInterface.isInternalType: Boolean
    get() = typeNameIsInternal(name)

private val loggingPackages = setOf(
    "org.hibernate.validator.internal.util.logging",
    "org.apache.commons.logging",
    "org.slf4j",
)

private val Class<*>.isLogger: Boolean
    get() = loggingPackages.any { packageName.startsWith(it) }

private val JcClassOrInterface.isLogger: Boolean
    get() = loggingPackages.any { packageName.startsWith(it) }

private val String.inImmutableFromJavaLang: Boolean
    get() = this.startsWith("java.lang")
            && this != "java.lang.StringBuilder"
            && this != "java.lang.StringBuffer"

private val Class<*>.inImmutableWithSubtypesFromJavaLang: Boolean
    get() = this.name.inImmutableFromJavaLang && (isFinal || !isPublic)

internal val Class<*>.isImmutable: Boolean
    get() = !isArray &&
            (immutableTypes.any { it.isAssignableFrom(this) }
                    || isPrimitive
                    || isEnum
                    || isRecord
                    || packagesWithImmutableTypes.any { packageName.startsWith(it) }
                    || this.name.inImmutableFromJavaLang
                    || isClassLoader
                    || isLogger
                    || isInternalType
                    || allFields.isEmpty())

internal val Class<*>.isImmutableWithSubtypes: Boolean
    get() = !isArray &&
            (immutableTypes.any { it.isAssignableFrom(this) }
                    || isPrimitive
                    || isEnum
                    || isRecord
                    || packagesWithImmutableTypes.any { packageName.startsWith(it) }
                    || inImmutableWithSubtypesFromJavaLang
                    || isClassLoader
                    || isLogger
                    || isInternalType
                    || allFields.isEmpty() && isFinal)

internal val JcClassOrInterface.isImmutable: Boolean
    get() = immutableTypes.any { this.allSuperHierarchyWithThis.any { cls -> cls.name == it.name } }
            || isEnum
            || packagesWithImmutableTypes.any { this.packageName.startsWith(it) }
            || this.name.inImmutableFromJavaLang
            || isClassLoader
            || isLogger
            || isInternalType
            || allFields.isEmpty()

internal val Class<*>.allInstanceFieldsAreFinal: Boolean
    get() = allInstanceFields.all { it.isFinal }

internal val Class<*>.isFinal: Boolean
    get() = Modifier.isFinal(modifiers)

internal val Class<*>.isPublic: Boolean
    get() = Modifier.isPublic(modifiers)

internal val Class<*>.isAbstract: Boolean
    get() = Modifier.isAbstract(modifiers)

private val JcClassOrInterface.allSuperHierarchyWithThis: Set<JcClassOrInterface>
    get() = allSuperHierarchy + this

private val JcClassOrInterface.isClassLoader: Boolean
    get() = allSuperHierarchyWithThis.any { it.name == "java.lang.ClassLoader" }

internal val Class<*>.isSolid: Boolean
    get() = notTracked || this.isArray && this.componentType.notTrackedWithSubtypes

internal val Class<*>.isSolidWithSubtypes: Boolean
    get() = notTrackedWithSubtypes || this.isArray && this.componentType.notTrackedWithSubtypes

class LambdaClassSource(
    override val location: RegisteredLocation,
    override val className: String,
    private val fileName: String
) : ClassSource {
    override val byteCode by lazy {
        location.jcLocation?.resolve(fileName) ?: className.throwClassNotFound()
    }
}

fun Class<*>.toJcType(ctx: JcContext): JcType? {
    try {
        if (isProxy) {
            val interfaces = interfaces
            if (interfaces.size == 1)
                return ctx.cp.findTypeOrNull(interfaces[0].typeName)

            return null
        }

        if (isLambda) {
            val cachedType = ctx.cp.findTypeOrNull(name)
            if (cachedType != null && cachedType !is JcUnknownType)
                return cachedType

            // TODO: add dynamic load of classes into jacodb
            val db = ctx.cp.db
            val vfs = db.javaClass.allInstanceFields.find { it.name == "classesVfs" }!!.getFieldValue(db)!!
            val lambdasDir = System.getenv("lambdasDir")
            val loc = ctx.cp.registeredLocations.find {
                it.jcLocation?.jarOrFolder?.absolutePath == lambdasDir
            }!!
            val addMethod = vfs.javaClass.methods.find { it.name == "addClass" }!!
            val fileName = getLambdaCanonicalTypeName(name)
            val source = LambdaClassSource(loc, name, fileName)
            addMethod.invoke(vfs, source)

            val type = ctx.cp.findTypeOrNull(name)
            check(type is JcClassType)
            JcLambdaFeature.addLambdaClass(this, type.jcClass)
            return type
        }

        return ctx.cp.findTypeOrNull(this.typeName)
    } catch (e: Throwable) {
        return null
    }
}



private fun createProxy(jcClass: JcClassOrInterface): Any {
    check(jcClass.isInterface)
    return Proxy.newProxyInstance(
        JcConcreteMemoryClassLoader,
        arrayOf(JcConcreteMemoryClassLoader.loadClass(jcClass)),
        LambdaInvocationHandler()
    )
}

internal fun createDefault(type: JcType): Any? {
    try {
        return when (type) {
            is JcArrayType -> type.allocateInstance(JcConcreteMemoryClassLoader, 1)
            is JcClassType -> {
                val jcClass = type.jcClass
                when {
                    jcClass.isInterface -> createProxy(jcClass)
                    jcClass.isAbstract -> null
                    else -> type.allocateInstance(JcConcreteMemoryClassLoader)
                }
            }
            is JcPrimitiveType -> null
            else -> error("createDefault: unexpected type $type")
        }
    } catch (e: Throwable) {
        println("[WARNING] failed to allocate ${type.internalName}")
        return null
    }
}

val String.typeName: TypeName
    get() = TypeNameImpl.fromTypeName(this)

val JcField.typedField: JcTypedField
    get() =
        enclosingClass.toType().findFieldOrNull(name)
            ?: error("Could not find field $this in type $enclosingClass")

fun JcContext.jcTypeOf(obj: Any): JcType {
    val type = cp.findType(obj.javaClass.typeName)
    if (type !is JcClassType) return type
    val jcClass = type.jcClass
    val approximateAnnotation =
        jcClass.annotations.find { it.matches("org.jacodb.approximation.annotation.Approximate") }
            ?: return type
    val approximatedClass = approximateAnnotation.values["value"] as JcClassOrInterface
    return approximatedClass.toType()
}
