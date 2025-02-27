package org.usvm.util

import org.jacodb.api.jvm.*
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.findFieldOrNull
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.jvmName
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.jacodb.impl.types.JcClassTypeImpl
import org.jacodb.impl.types.TypeNameImpl
import org.usvm.UConcreteHeapRef
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.JcContext
import org.usvm.machine.JcTransparentInstruction
import org.usvm.memory.ULValue
import org.usvm.memory.UWritableMemory
import org.usvm.uctx
import kotlin.reflect.KClass

fun JcContext.extractJcType(clazz: KClass<*>): JcType = cp.findTypeOrNull(clazz.qualifiedName!!)!!

fun JcContext.extractJcRefType(clazz: KClass<*>): JcRefType = extractJcType(clazz) as JcRefType

val JcClassOrInterface.enumValuesField: JcTypedField
    get() = toType().findFieldOrNull("\$VALUES") ?: error("No \$VALUES field found for the enum type $this")

@Suppress("UNCHECKED_CAST")
fun UWritableMemory<*>.write(ref: ULValue<*, *>, value: UExpr<*>) {
    write(ref as ULValue<*, USort>, value as UExpr<USort>, value.uctx.trueExpr)
}

internal fun UWritableMemory<JcType>.allocHeapRef(type: JcType, useStaticAddress: Boolean): UConcreteHeapRef =
    if (useStaticAddress) allocStatic(type) else allocConcrete(type)

tailrec fun JcInst.originalInst(): JcInst = if (this is JcTransparentInstruction) originalInst.originalInst() else this

val JcClassType.name: String
    get() = if (this is JcClassTypeImpl) name else jcClass.name

val JcClassType.outerClassInstanceField: JcTypedField?
    get() = fields.singleOrNull { it.name == "this\$0" }

fun JcContext.classesOfLocations(locations: List<JcByteCodeLocation>): Sequence<JcClassOrInterface> {
    return locations
        .asSequence()
        .flatMap { it.classNames ?: emptySet() }
        .mapNotNull { cp.findClassOrNull(it) }
        .filterNot { it is JcUnknownClass }
}
