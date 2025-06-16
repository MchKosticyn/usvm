package machine.interpreter.transformers.springjpa.query.expresion

import machine.interpreter.transformers.springjpa.JAVA_INIT
import machine.interpreter.transformers.springjpa.methodRef
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.type.Null
import machine.interpreter.transformers.springjpa.query.type.Primitive
import machine.interpreter.transformers.springjpa.query.type.SqlType
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcByte
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcDouble
import org.jacodb.api.jvm.cfg.JcFloat
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcLong
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcNewExpr
import org.jacodb.api.jvm.cfg.JcNullConstant
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcStaticCallExpr
import org.jacodb.api.jvm.cfg.JcStringConstant
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import java.time.LocalDateTime

enum class Datetime {
    Year, Month, Day, Week, Quarter, Hour, Minute, Second, Nanosecond, Epoch
}

class LString(val value: String): NoLambdaExpression() {

    override val type = Primitive.String()

    override fun genInst(ctx: MethodCtx): JcLocalVar = with(ctx) {
        val v = newVar(common.strType)
        val str = JcStringConstant(value, common.strType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, str) }
        return v
    }
}

class LNull: NoLambdaExpression() {

    override val type = Null()

    override fun genInst(ctx: MethodCtx): JcLocalVar = with(ctx) {
        val v = newVar(cp.objectType)
        val n = JcNullConstant(cp.objectType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, n) }
        return v
    }
}

class LBool(val value: Boolean): NoLambdaExpression() {

    override val type = Primitive.Bool()

    override fun genInst(ctx: MethodCtx): JcLocalVar = with(ctx) {
        val v = newVar(common.boolType)
        val b = JcBool(value, common.boolType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, b) }
        return v
    }
}

class LInt(val value: Int): NoLambdaExpression() {

    override val type = Primitive.Int()

    override fun genInst(ctx: MethodCtx): JcLocalVar = with(ctx) {
        val intV = newVar(cp.int)
        val i = JcInt(value, cp.int)
        genCtx.addInstruction { loc -> JcAssignInst(loc, intV, i) }

        val integerV = newVar(common.integerType)
        val valueOf = common.integerType.declaredMethods.single {
            it.name == common.valueOfName && it.isStatic && it.parameters.size == 1
                    && it.parameters.single().type == cp.int
        }.methodRef
        val cast = JcStaticCallExpr(valueOf, listOf(intV))
        genCtx.addInstruction { loc -> JcAssignInst(loc, integerV, cast) }

        return integerV
    }
}

class LLong(val value: Long): NoLambdaExpression() {

    override val type = Primitive.Long()

    override fun genInst(ctx: MethodCtx): JcLocalVar = with(ctx) {
        val v = newVar(common.longType)
        val l = JcLong(value, common.longType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, l) }
        return v
    }
}

class LBigInt(val value: String): NoLambdaExpression() {

    override val type = Primitive.BigInt()

    override fun genInst(ctx: MethodCtx): JcLocalVar = with(ctx) {
        val v = newVar(common.bigIntType)
        val init = JcNewExpr(common.bigIntType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, init) }

        val bigInit = common.bigIntType.declaredMethods
            .single { it.name == JAVA_INIT && it.parameters.first().type == common.strType }
        val str = JcStringConstant(value, common.strType)
        val initCall = JcSpecialCallExpr(bigInit.methodRef, v, listOf(str))
        genCtx.addInstruction { loc -> JcCallInst(loc, initCall) }
        return v
    }
}

class LFloat(val value: Float): NoLambdaExpression() {

    override val type = Primitive.Float()

    override fun genInst(ctx: MethodCtx): JcLocalVar = with(ctx) {
        val v = newVar(common.floatType)
        val f = JcFloat(value, common.floatType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, f) }
        return v
    }
}

class LDouble(val value: Double): NoLambdaExpression() {

    override val type = Primitive.Double()

    override fun genInst(ctx: MethodCtx): JcLocalVar = with(ctx) {
        val v = newVar(common.doubleType)
        val d = JcDouble(value, common.doubleType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, d) }
        return v
    }
}

class LBigDecimal(val value: String): NoLambdaExpression() {

    override val type = Primitive.BigDecimal()

    override fun genInst(ctx: MethodCtx): JcLocalVar = with(ctx) {
        val v = newVar(common.bigDecimalType)
        val init = JcNewExpr(common.bigDecimalType)
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, init) }

        val bigInit = common.bigDecimalType.declaredMethods
            .single { it.name == JAVA_INIT && it.parameters.first().type == common.strType }
        val str = JcStringConstant(value, common.strType)
        val initCall = JcSpecialCallExpr(bigInit.methodRef, v, listOf(str))
        genCtx.addInstruction { loc -> JcCallInst(loc, initCall) }
        return v
    }
}

class LBinary(val bins: ByteArray): NoLambdaExpression() {

    override val type = Primitive.Binary()

    override fun genInst(ctx: MethodCtx): JcLocalVar = with(ctx) {
        val size = bins.size
        val v = newVar(common.byteArrType)
        val arr = JcNewArrayExpr(common.byteArrType, listOf(JcInt(size, cp.int)))
        genCtx.addInstruction { loc -> JcAssignInst(loc, v, arr) }

        bins.forEachIndexed { ix, b ->
            val elem = JcArrayAccess(v, JcInt(ix, cp.int), cp.byte)
            val value = JcByte(b, cp.byte)
            ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, elem, value) }
        }

        return v
    }
}

class LTime(val time: LocalDateTime): NoLambdaExpression() {

    override val type: SqlType
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
