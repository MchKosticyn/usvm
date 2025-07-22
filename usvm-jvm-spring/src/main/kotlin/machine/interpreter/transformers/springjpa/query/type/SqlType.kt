package machine.interpreter.transformers.springjpa.query.type

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.Parameter
import machine.interpreter.transformers.springjpa.query.path.SimplePath
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.findType
import org.usvm.jvm.util.toJcType

abstract class SqlType {
    abstract fun getType(info: CommonInfo): JcType
}

class Null : SqlType() {
    override fun getType(info: CommonInfo): JcType {
        TODO("Not yet implemented")
    }
}

class Param(val param: Parameter) : SqlType() {
    override fun getType(info: CommonInfo): JcType {
        val pos = param.position(info)
        return info.origMethod.parameters.get(pos).type.toJcType(info.cp)!!
    }
}

class Path(val name: SimplePath) : SqlType() {
    override fun getType(info: CommonInfo): JcType = with(info) {
        val aliased = aliases[name.root] ?: name.root
        val baseClassName = collector.getTableByPartName(aliased).single().origClassName
        val base = cp.findType(baseClassName)
        return if (name.isSimple()) {
            base
        } else {
            name.cont.fold(base) { acc, nameField ->
                collector.collectFields((acc as JcClassType).jcClass)
                    .single { it.name == nameField }.type.toJcType(cp)!!
            }
        }
    }
}

class Tuple(val types: List<SqlType>) : SqlType() {
    override fun getType(info: CommonInfo): JcType {
        TODO("Not yet implemented")
    }
}
