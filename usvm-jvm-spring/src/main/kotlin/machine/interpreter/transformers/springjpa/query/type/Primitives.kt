package machine.interpreter.transformers.springjpa.query.type

import machine.interpreter.transformers.springjpa.query.CommonInfo
import org.jacodb.api.jvm.JcType

abstract class Primitive : Type() {
    class String : Primitive() {
        override fun getType(info: CommonInfo): JcType = info.strType
    }

    class Bool : Primitive() {
        override fun getType(info: CommonInfo): JcType = info.boolType
    }

    class Int : Primitive() {
        override fun getType(info: CommonInfo): JcType = info.integerType
    }

    class Long : Primitive() {
        override fun getType(info: CommonInfo): JcType = info.longType
    }

    class Float : Primitive() {
        override fun getType(info: CommonInfo): JcType = info.longType
    }

    class Double : Primitive() {
        override fun getType(info: CommonInfo): JcType = info.doubleType
    }

    class BigInt : Primitive() {
        override fun getType(info: CommonInfo): JcType = info.bigIntType
    }

    class BigDecimal : Primitive() {
        override fun getType(info: CommonInfo): JcType = info.bigDecimalType
    }

    class Binary : Primitive() {
        override fun getType(info: CommonInfo): JcType = info.byteArrType
    }
}
