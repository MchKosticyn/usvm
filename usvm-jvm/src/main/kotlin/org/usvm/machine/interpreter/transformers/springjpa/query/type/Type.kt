package org.usvm.machine.interpreter.transformers.springjpa.query.type

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.toType
import org.usvm.jvm.util.toJcType
import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo
import org.usvm.machine.interpreter.transformers.springjpa.query.Parameter
import org.usvm.machine.interpreter.transformers.springjpa.query.path.SimplePath

abstract class Type {
    abstract fun getType(info: CommonInfo): JcType
}

class Null : Type() {
    override fun getType(info: CommonInfo): JcType {
        TODO("Not yet implemented")
    }
}

class Param(val param: Parameter) : Type() {
    override fun getType(info: CommonInfo): JcType {
        val pos = param.position(info)
        return info.origMethod.parameters.get(pos).type.toJcType(info.cp)!!
    }
}

class Path(val name: SimplePath) : Type() {
    override fun getType(info: CommonInfo): JcType {
        val aliased = info.aliases[name.root] ?: name.root
        val base = info.collector.getTableByPartName(aliased).single().origClass.toType()
        return if (name.isSimple()) {
            base
        } else {
            name.cont.fold(base as JcType) { acc, nameField ->
                info.collector.collectFields((acc as JcClassType).jcClass)
                    .single { it.name == nameField }.type.toJcType(info.cp)!!
            }
        }
    }
}

class Tuple(val types: List<Type>) : Type() {
    override fun getType(info: CommonInfo): JcType {
        TODO("Not yet implemented")
    }
}
