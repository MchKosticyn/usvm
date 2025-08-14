package machine.interpreter.transformers.springjpa.query.type

import jpa.JAVA_LIST
import jpa.getColumnName
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.parameter.position
import machine.interpreter.transformers.springjpa.query.path.isSimple
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.findType
import org.usvm.jvm.util.toJcType
import org.usvm.spring.query.type.AType
import org.usvm.spring.query.type.ITypeVisitor
import org.usvm.spring.query.type.TNull
import org.usvm.spring.query.type.TParam
import org.usvm.spring.query.type.TPath
import org.usvm.spring.query.type.TTuple
import org.usvm.spring.query.type.primitive.TBigDecimal
import org.usvm.spring.query.type.primitive.TBigInt
import org.usvm.spring.query.type.primitive.TBinary
import org.usvm.spring.query.type.primitive.TBool
import org.usvm.spring.query.type.primitive.TDouble
import org.usvm.spring.query.type.primitive.TFloat
import org.usvm.spring.query.type.primitive.TInt
import org.usvm.spring.query.type.primitive.TList
import org.usvm.spring.query.type.primitive.TLocalDate
import org.usvm.spring.query.type.primitive.TLong
import org.usvm.spring.query.type.primitive.TString

fun AType.getType(info: CommonInfo): JcType = this.accept(typeGetTypeVisitor, info)
private val typeGetTypeVisitor = object : ITypeVisitor<JcType, CommonInfo> {

    override fun visit(child: TBigDecimal, ctx: CommonInfo) = ctx.bigDecimalType

    override fun visit(child: TBigInt, ctx: CommonInfo) = ctx.bigIntType

    override fun visit(child: TBinary, ctx: CommonInfo) = ctx.byteArrType

    override fun visit(child: TBool, ctx: CommonInfo) = ctx.boolType

    override fun visit(child: TDouble, ctx: CommonInfo) = ctx.doubleType

    override fun visit(child: TFloat, ctx: CommonInfo) = ctx.floatType

    override fun visit(child: TInt, ctx: CommonInfo) = ctx.integerType

    override fun visit(child: TList, ctx: CommonInfo) = ctx.cp.findType(JAVA_LIST)

    override fun visit(child: TLocalDate, ctx: CommonInfo) = ctx.localDateType

    override fun visit(child: TLong, ctx: CommonInfo) = ctx.longType

    override fun visit(child: TString, ctx: CommonInfo) = ctx.strType

    override fun visit(child: TNull, ctx: CommonInfo): JcType {
        TODO("Not yet implemented")
    }

    override fun visit(child: TParam, ctx: CommonInfo) = with(child) {
        val pos = param.position(ctx)
        ctx.origMethod.parameters.get(pos).type.toJcType(ctx.cp)!!
    }

    override fun visit(child: TPath, ctx: CommonInfo) = with(ctx) {
        val aliased = aliases[child.name.root] ?: child.name.root
        val baseClassTable = collector.getTableByPartName(aliased).single()
        val base = cp.findType(baseClassTable.origClassName)
        if (child.name.isSimple()) {
            base
        } else {
            child.name.cont.fold(base) { acc, nameField ->
                collector.collectFields((acc as JcClassType).jcClass)
                    .single { it.name == nameField || getColumnName(it) == nameField.lowercase() }.type.toJcType(cp)!!
            }
        }
    }

    override fun visit(child: TTuple, ctx: CommonInfo): JcType {
        TODO("Not yet implemented")
    }
}
