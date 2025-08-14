package machine.interpreter.transformers.springjpa.query

import org.jacodb.api.jvm.JcMethod

abstract class Lambdable {
    abstract fun getLambdas(info: CommonInfo): List<JcMethod>
}

open class SingleLambdable(val lambdable: Lambdable) : Lambdable() {
    override fun getLambdas(info: CommonInfo) = lambdable.getLambdas(info)
}

open class DoubleLambdable(val left: Lambdable, val right: Lambdable) : Lambdable() {
    override fun getLambdas(info: CommonInfo) = left.getLambdas(info) + right.getLambdas(info)
}

open class ManyLambdable(val lambdables: List<Lambdable>) : Lambdable() {
    override fun getLambdas(info: CommonInfo) = lambdables.flatMap { it.getLambdas(info) }
}

abstract class SingleWithOwnLambdable(val lambdable: Lambdable) : Lambdable() {
    abstract fun getOwnMethod(info: CommonInfo): JcMethod

    override fun getLambdas(info: CommonInfo) = lambdable.getLambdas(info) + getOwnMethod(info)
}

abstract class ManyWithOwnLambdable(val lambdables: List<Lambdable>) : Lambdable() {
    abstract fun getOwnMethod(info: CommonInfo): JcMethod

    override fun getLambdas(info: CommonInfo) = lambdables.flatMap { it.getLambdas(info) } + getOwnMethod(info)
}
