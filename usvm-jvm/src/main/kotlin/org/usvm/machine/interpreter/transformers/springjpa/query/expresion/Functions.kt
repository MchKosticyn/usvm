package org.usvm.machine.interpreter.transformers.springjpa.query.expresion

import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.Parameter
import org.usvm.machine.interpreter.transformers.springjpa.query.function.FunctionCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.path.PathCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.path.SimplePathCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.type.Primitive
import org.usvm.machine.interpreter.transformers.springjpa.query.type.TypeCtx

class TypeOfParam(val param: Parameter) : ExpressionCtx() {

    override val type: TypeCtx
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class TypeOfPath(val path: PathCtx) : ExpressionCtx() {

    override val type: TypeCtx
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Id(val path: PathCtx, val cont: SimplePathCtx?) : ExpressionCtx() {

    override val type = Primitive.Bool()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Version(val path: PathCtx) : ExpressionCtx() {

    override val type: TypeCtx
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class NaturalId(val path: PathCtx, val cont: SimplePathCtx?) : ExpressionCtx() {

    override val type: TypeCtx
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}


class FunctionExpr(val function: FunctionCtx) : ExpressionCtx() {

    override val type: TypeCtx
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}


class Minus(val expr: ExpressionCtx) : ExpressionCtx() {

    override val type = expr.type

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class ToDuration(val expr: ExpressionCtx, val datetime: Datetime) : ExpressionCtx() {
    override val type: TypeCtx
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class FromDuration(val expr: ExpressionCtx, val datetime: Datetime) : ExpressionCtx() {
    override val type: TypeCtx
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class BinOperator(val left: ExpressionCtx, val right: ExpressionCtx, val operator: Operator) : ExpressionCtx() {

    override val type = left.type

    enum class Operator {
        Slash, Percent, Asterisk, Plus, Minus, Concat
    }

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
