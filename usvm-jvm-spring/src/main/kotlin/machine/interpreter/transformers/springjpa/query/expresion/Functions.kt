package machine.interpreter.transformers.springjpa.query.expresion

import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.Parameter
import machine.interpreter.transformers.springjpa.query.path.Path
import machine.interpreter.transformers.springjpa.query.path.SimplePath
import machine.interpreter.transformers.springjpa.query.type.Primitive
import machine.interpreter.transformers.springjpa.query.type.SqlType
import org.jacodb.api.jvm.cfg.JcLocalVar

class TypeOfParameter(val param: Parameter) : NoLambdaExpression() {

    override val type: SqlType
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class TypeOfPath(val path: Path) : NoLambdaExpression() {

    override val type: SqlType
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Id(val path: Path, val cont: SimplePath?) : NoLambdaExpression() {

    override val type = Primitive.Bool()

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Version(val path: Path) : NoLambdaExpression() {

    override val type: SqlType
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class NaturalId(val path: Path, val cont: SimplePath?) : NoLambdaExpression() {

    override val type: SqlType
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class Minus(expr: Expression) : SingleArgumentExpression(expr) {

    override val type = expr.type

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class ToDuration(expr: Expression, val datetime: Datetime) : SingleArgumentExpression(expr) {
    override val type: SqlType
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class FromDuration(expr: Expression, val datetime: Datetime) : SingleArgumentExpression(expr) {
    override val type: SqlType
        get() = TODO("Not yet implemented")

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}

class BinOperator(left: Expression, right: Expression, val operator: Operator) : DoubleArgumentExpression(left, right) {

    override val type = left.type

    enum class Operator {
        Slash, Percent, Asterisk, Plus, Minus, Concat
    }

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }
}
