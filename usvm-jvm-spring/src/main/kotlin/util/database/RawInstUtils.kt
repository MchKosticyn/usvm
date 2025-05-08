package util.database

import machine.interpreter.transformers.springjpa.JAVA_BOOL
import machine.interpreter.transformers.springjpa.JAVA_BYTE
import machine.interpreter.transformers.springjpa.JAVA_CHAR
import machine.interpreter.transformers.springjpa.JAVA_DOUBLE
import machine.interpreter.transformers.springjpa.JAVA_FLOAT
import machine.interpreter.transformers.springjpa.JAVA_INTEGER
import machine.interpreter.transformers.springjpa.JAVA_LONG
import machine.interpreter.transformers.springjpa.JAVA_SHORT
import machine.interpreter.transformers.springjpa.JAVA_STRING
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcRawAssignInst
import org.jacodb.api.jvm.cfg.JcRawBool
import org.jacodb.api.jvm.cfg.JcRawByte
import org.jacodb.api.jvm.cfg.JcRawChar
import org.jacodb.api.jvm.cfg.JcRawConstant
import org.jacodb.api.jvm.cfg.JcRawDouble
import org.jacodb.api.jvm.cfg.JcRawExpr
import org.jacodb.api.jvm.cfg.JcRawFloat
import org.jacodb.api.jvm.cfg.JcRawInt
import org.jacodb.api.jvm.cfg.JcRawLocalVar
import org.jacodb.api.jvm.cfg.JcRawLong
import org.jacodb.api.jvm.cfg.JcRawNullConstant
import org.jacodb.api.jvm.cfg.JcRawShort
import org.jacodb.api.jvm.cfg.JcRawStaticCallExpr
import org.jacodb.api.jvm.ext.JAVA_OBJECT
import org.jacodb.impl.cfg.JcRawString
import org.usvm.jvm.util.typeName

fun JcMethodNewBodyContext.putValueToVar(value: JcRawExpr, typ: TypeName): JcRawLocalVar {
    val variable = newVar(typ)
    addInstruction { owner -> JcRawAssignInst(owner, variable, value) }
    return variable
}

private fun <T> JcMethodNewBodyContext.buildConstantWithUpcast(
    value: T,
    typ: String,
    upType: String,
    build: (T, TypeName) -> JcRawConstant
) = buildConstantWithUpcast(value, typ.typeName, upType.typeName, build)

private fun <T> JcMethodNewBodyContext.buildConstantWithUpcast(
    value: T,
    typ: TypeName,
    upType: TypeName,
    build: (T, TypeName) -> JcRawConstant
) : JcRawLocalVar {
    val const = putValueToVar(build(value, typ), typ)

    val upcastCall = JcRawStaticCallExpr(
        upType,
        "valueOf",
        listOf(typ),
        upType,
        listOf(const)
    )
    val upcast = putValueToVar(upcastCall, upType)
    return upcast
}

fun JcMethodNewBodyContext.resolveRawObject(value: Any?): JcRawLocalVar {

    if (value == null) return putValueToVar(JcRawNullConstant(JAVA_OBJECT.typeName), JAVA_OBJECT.typeName)

    return when (value) {
        is Boolean -> buildConstantWithUpcast(value, PredefinedPrimitives.Boolean, JAVA_BOOL, ::JcRawBool)
        is Byte -> buildConstantWithUpcast(value, PredefinedPrimitives.Byte, JAVA_BYTE, ::JcRawByte)
        is Char -> buildConstantWithUpcast(value, PredefinedPrimitives.Char, JAVA_CHAR, ::JcRawChar)
        is Double -> buildConstantWithUpcast(value, PredefinedPrimitives.Double, JAVA_DOUBLE, ::JcRawDouble)
        is Float -> buildConstantWithUpcast(value, PredefinedPrimitives.Float, JAVA_FLOAT, ::JcRawFloat)
        is Int -> buildConstantWithUpcast(value, PredefinedPrimitives.Int, JAVA_INTEGER, ::JcRawInt)
        is Long -> buildConstantWithUpcast(value, PredefinedPrimitives.Long, JAVA_LONG,  ::JcRawLong)
        is Short -> buildConstantWithUpcast(value, PredefinedPrimitives.Short, JAVA_SHORT, ::JcRawShort)
        is String -> putValueToVar(JcRawString(value), JAVA_STRING.typeName)
        else -> error("resolveRawObject: unexpected type")
    }
}
