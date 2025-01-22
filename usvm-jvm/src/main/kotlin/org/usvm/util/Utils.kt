package org.usvm.util

import org.jacodb.api.jvm.*
import org.jacodb.api.jvm.cfg.JcInst
import org.jacodb.api.jvm.ext.findFieldOrNull
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.jvmName
import org.jacodb.api.jvm.ext.toType
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

val JcField.typedField: JcTypedField
    get() =
        enclosingClass.toType().findFieldOrNull(name)
            ?: error("Could not find field $this in type $enclosingClass")

fun JcContext.jcTypeOf(obj: Any): JcType {
    val type = cp.findType(obj.javaClass.typeName)
    if (type !is JcClassTypeImpl) return type
    val jcClass = type.jcClass
    val approximateAnnotation =
        jcClass.annotations.find { it.matches("org.jacodb.approximation.annotation.Approximate") }
            ?: return type
    val approximatedClass = approximateAnnotation.values["value"] as JcClassOrInterface
    return approximatedClass.toType()
}

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

val String.typeName: TypeName
    get() = TypeNameImpl(this.jvmName())
