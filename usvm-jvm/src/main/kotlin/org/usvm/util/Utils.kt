package org.usvm.util

import org.jacodb.api.jvm.*
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.jacodb.impl.types.JcClassTypeImpl
import org.jacodb.impl.types.TypeNameImpl
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.JcContext
import org.usvm.machine.JcTransparentInstruction
import org.usvm.machine.interpreter.transformers.springjpa.JAVA_VOID
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.uctx
import kotlin.reflect.KClass

fun JcContext.classesOfLocations(locations: List<JcByteCodeLocation>): Sequence<JcClassOrInterface> {
    return locations
        .asSequence()
        .flatMap { it.classNames ?: emptySet() }
        .mapNotNull { cp.findClassOrNull(it) }
        .filterNot { it is JcUnknownClass }
}

fun JcContext.extractJcType(clazz: KClass<*>): JcType = cp.findTypeOrNull(clazz.qualifiedName!!)!!

fun JcContext.extractJcRefType(clazz: KClass<*>): JcRefType = extractJcType(clazz) as JcRefType

@Suppress("UNCHECKED_CAST")
fun UWritableMemory<*>.write(ref: ULValue<*, *>, value: UExpr<*>) {
    write(ref as ULValue<*, USort>, value as UExpr<USort>, value.uctx.trueExpr)
}

internal fun UWritableMemory<JcType>.allocHeapRef(type: JcType, useStaticAddress: Boolean): UConcreteHeapRef =
    if (useStaticAddress) allocStatic(type) else allocConcrete(type)

tailrec fun JcInst.originalInst(): JcInst = if (this is JcTransparentInstruction) originalInst.originalInst() else this

fun JcMethod.isSame(other: JcMethod) =
    this.name == other.name && this.description == other.description && this.signature == other.signature

val JcMethod.isVoid: Boolean get() = returnType.typeName == JAVA_VOID

val JcClassType.name: String
    get() = if (this is JcClassTypeImpl) name else jcClass.name

val JcClassType.outerClassInstanceField: JcTypedField?
    get() = fields.singleOrNull { it.name == "this\$0" }

val String.typeName: TypeName
    get() = TypeNameImpl.fromTypeName(this)

val JcClassOrInterface.jvmDescriptor : String get() = "L${name.replace('.','/')};"

val String.fromJvmDescriptor : String get() {
    var s = if (this.startsWith("[")) this.drop(1).plus("[]") else this
    s = s.replace("/", ".")
    return if (s.startsWith("L")) s.drop(1) else s
}

val String.genericTypes : List<String> get() = this
    .substringAfter("<")
    .substringBefore(">")
    .split(";")
    .filter{ it.isNotEmpty() }
    .map { it.fromJvmDescriptor }
