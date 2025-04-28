package org.usvm.machine.interpreter.transformers.springjpa.query.expresion

import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.Parameter
import org.usvm.machine.interpreter.transformers.springjpa.query.function.Function
import org.usvm.machine.interpreter.transformers.springjpa.query.path.Path
import org.usvm.machine.interpreter.transformers.springjpa.query.path.SimplePath
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Primitive
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Type

class TypeOfParameter(val param: Parameter) : Expression() {

    override val type: Type
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class TypeOfPath(val path: Path) : Expression() {

    override val type: Type
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Id(val path: Path, val cont: SimplePath?) : Expression() {

    override val type = Primitive.Bool()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Version(val path: Path) : Expression() {

    override val type: Type
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class NaturalId(val path: Path, val cont: SimplePath?) : Expression() {

    override val type: Type
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}


class FunctionExpr(val function: Function) : Expression() {

    override val type: Type
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}


class Minus(val expr: Expression) : Expression() {

    override val type = expr.type

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class ToDuration(val expr: Expression, val datetime: Datetime) : Expression() {
    override val type: Type
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class FromDuration(val expr: Expression, val datetime: Datetime) : Expression() {
    override val type: Type
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class BinOperator(val left: Expression, val right: Expression, val operator: Operator) : Expression() {

    override val type = left.type

    enum class Operator {
        Slash, Percent, Asterisk, Plus, Minus, Concat
    }

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
