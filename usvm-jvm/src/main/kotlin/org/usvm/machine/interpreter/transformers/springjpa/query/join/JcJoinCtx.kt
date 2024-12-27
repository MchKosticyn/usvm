package org.usvm.machine.interpreter.transformers.springjpa.query.join

import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx

abstract class JoinCtx() {

    open fun getAlias(): Pair<String, String>? {
        return null
    }

    abstract fun positions(info: CommonInfo): List<String>
    abstract fun getLambdas(info: CommonInfo): List<JcMethod>
    abstract fun collectNames(info: CommonInfo): Map<String, List<JcField>>
    abstract fun genJoin(ctx: MethodCtx, name: String, root: JcLocalVar): JcLocalVar

    class JpaCollectionJoin : JoinCtx() { // deprecated syntax (never documented)
        override fun positions(info: CommonInfo): List<String> {
            TODO("Not yet implemented")
        }

        override fun getLambdas(info: CommonInfo): List<JcMethod> {
            TODO("Not yet implemented")
        }

        override fun collectNames(info: CommonInfo): Map<String, List<JcField>> {
            TODO("Not yet implemented")
        }

        override fun genJoin(ctx: MethodCtx, name: String, root: JcLocalVar): JcLocalVar {
            TODO("Not yet implemented")
        }
    }

    class CrossJoin : JoinCtx() {
        override fun positions(info: CommonInfo): List<String> {
            TODO("Not yet implemented")
        }

        override fun getLambdas(info: CommonInfo): List<JcMethod> {
            TODO("Not yet implemented")
        }

        override fun collectNames(info: CommonInfo): Map<String, List<JcField>> {
            TODO("Not yet implemented")
        }

        override fun genJoin(ctx: MethodCtx, name: String, root: JcLocalVar): JcLocalVar {
            TODO("Not yet implemented")
        }
    }
}
