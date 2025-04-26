package org.usvm.jvm.util

import org.jacodb.api.jvm.*
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.*
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.jacodb.impl.types.JcClassTypeImpl
import org.jacodb.impl.types.TypeNameImpl
import org.objectweb.asm.tree.MethodNode
import java.lang.reflect.Constructor
import java.lang.reflect.Executable
import java.lang.reflect.Field
import java.lang.reflect.Method
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaMethod

val JcClasspath.stringType: JcType
    get() = findClassOrNull("java.lang.String")!!.toType()

fun JcClasspath.findFieldByFullNameOrNull(fieldFullName: String): JcField? {
    val className = fieldFullName.substringBeforeLast('.')
    val fieldName = fieldFullName.substringAfterLast('.')
    val jcClass = findClassOrNull(className) ?: return null
    return jcClass.declaredFields.find { it.name == fieldName }
}

operator fun JcClasspath.get(klass: Class<*>) = this.findClassOrNull(klass.typeName)

val JcClassOrInterface.typename
    get() = TypeNameImpl.fromTypeName(this.name)

fun JcType.toStringType(): String =
    when (this) {
        is JcClassType -> jcClass.name
        is JcTypeVariable -> jcClass.name
        is JcArrayType -> "${elementType.toStringType()}[]"
        else -> typeName
    }

fun JcType.getTypename() = TypeNameImpl.fromTypeName(this.typeName)

val JcInst.enclosingClass
    get() = this.location.method.enclosingClass

val JcInst.enclosingMethod
    get() = this.location.method

fun Class<*>.toJcType(jcClasspath: JcClasspath): JcType? {
    return jcClasspath.findTypeOrNull(this.typeName)
}

fun JcType.toJcClass(): JcClassOrInterface? =
    when (this) {
        is JcRefType -> jcClass
        is JcPrimitiveType -> null
        else -> error("Unexpected type")
    }

fun JcField.toJavaField(classLoader: ClassLoader): Field? =
    enclosingClass.toType().toJavaClass(classLoader).getFieldByName(name)

val JcClassOrInterface.allDeclaredFields
    get(): List<JcField> {
        val result = HashMap<String, JcField>()
        var current: JcClassOrInterface? = this
        do {
            current!!.declaredFields.forEach {
                result.putIfAbsent("${it.name}${it.type}", it)
            }
            current = current.superClass
        } while (current != null)
        return result.values.toList()
    }

fun TypeName.toJcType(jcClasspath: JcClasspath): JcType? = jcClasspath.findTypeOrNull(typeName)
fun TypeName.toJcClassOrInterface(jcClasspath: JcClasspath): JcClassOrInterface? = jcClasspath.findClassOrNull(typeName)

fun JcMethod.toJavaExecutable(classLoader: ClassLoader): Executable? {
    val type = enclosingClass.toType().toJavaClass(classLoader)
    return (type.methods + type.declaredMethods).find { it.jcdbSignature == this.jcdbSignature }
        ?: (type.constructors + type.declaredConstructors).find { it.jcdbSignature == this.jcdbSignature }
}

fun JcMethod.toJavaMethod(classLoader: ClassLoader): Method {
    val klass = Class.forName(enclosingClass.name, false, classLoader)
    return (klass.methods + klass.declaredMethods).find { it.isSameSignatures(this) }
        ?: throw MethodNotFoundException("Can't find method $name in classpath")
}

fun JcMethod.toJavaConstructor(classLoader: ClassLoader): Constructor<*> {
    require(isConstructor) { "Can't convert not constructor to constructor" }
    val klass = Class.forName(enclosingClass.name, true, classLoader)
    return (klass.constructors + klass.declaredConstructors).find { it.jcdbSignature == this.jcdbSignature }
        ?: throw MethodNotFoundException("Can't find constructor of class ${enclosingClass.name}")
}

val Method.jcdbSignature: String
    get() {
        val parameterTypesAsString = parameterTypes.toJcdbFormat()
        return name + "(" + parameterTypesAsString + ")" + returnType.typeName + ";"
    }

val Constructor<*>.jcdbSignature: String
    get() {
        val methodName = "<init>"
        //Because of jcdb
        val returnType = "void;"
        val parameterTypesAsString = parameterTypes.toJcdbFormat()
        return "$methodName($parameterTypesAsString)$returnType"
    }

private fun Array<Class<*>>.toJcdbFormat(): String =
    if (isEmpty()) "" else joinToString(";", postfix = ";") { it.typeName }

fun Method.isSameSignatures(jcMethod: JcMethod) =
    jcdbSignature == jcMethod.jcdbSignature

fun Constructor<*>.isSameSignatures(jcMethod: JcMethod) =
    jcdbSignature == jcMethod.jcdbSignature

fun JcMethod.isSameSignature(mn: MethodNode): Boolean =
    withAsmNode { it.isSameSignature(mn) }

val JcMethod.toTypedMethod: JcTypedMethod
    get() = this.enclosingClass.toType().declaredMethods.first { typed -> typed.method == this }

val JcClassOrInterface.enumValuesField: JcTypedField
    get() = toType().findFieldOrNull("\$VALUES") ?: error("No \$VALUES field found for the enum type $this")

val JcClassType.name: String
    get() = if (this is JcClassTypeImpl) name else jcClass.name

val JcClassType.outerClassInstanceField: JcTypedField?
    get() = fields.singleOrNull { it.name == "this\$0" }

@Suppress("RecursivePropertyAccessor")
val JcClassType.allFields: List<JcTypedField>
    get() = declaredFields + (superType?.allFields ?: emptyList())

@Suppress("RecursivePropertyAccessor")
val JcClassOrInterface.allFields: List<JcField>
    get() = declaredFields + (superClass?.allFields ?: emptyList())

val JcClassType.allInstanceFields: List<JcTypedField>
    get() = allFields.filter { !it.isStatic }

val kotlin.reflect.KProperty<*>.javaName: String
    get() = this.javaField?.name ?: error("No java name for field $this")

val kotlin.reflect.KFunction<*>.javaName: String
    get() = this.javaMethod?.name ?: error("No java name for method $this")
