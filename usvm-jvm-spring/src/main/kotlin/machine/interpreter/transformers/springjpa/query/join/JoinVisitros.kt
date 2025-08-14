package machine.interpreter.transformers.springjpa.query.join

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.path.getAlias
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.spring.query.join.AJoin
import org.usvm.spring.query.join.CrossJoin
import org.usvm.spring.query.join.IJoinVisitor
import org.usvm.spring.query.join.JpaCollectionJoin
import org.usvm.spring.query.join.common.CommonJoin

fun AJoin.getAlias(): Pair<String, String>? = this.accept(joinGetAliasVisitor, null, null)
private val joinGetAliasVisitor = object : IJoinVisitor<Pair<String, String>?, Any?, Any?> {
    override fun visit(child: CrossJoin, args: Any?, ctx: Any?) = null

    override fun visit(child: JpaCollectionJoin, args: Any?, ctx: Any?) = null

    override fun visit(child: CommonJoin, args: Any?, ctx: Any?) = with(child) { target.getAlias() }
}


fun AJoin.positions(info: CommonInfo): List<String> = this.accept(joinPositionsVisitor, null, info)
private val joinPositionsVisitor = object : IJoinVisitor<List<String>, CommonInfo, Any?> {
    override fun visit(child: CrossJoin, args: Any?, ctx: CommonInfo): List<String> {
        TODO("Not yet implemented")
    }

    override fun visit(child: JpaCollectionJoin, args: Any?, ctx: CommonInfo): List<String> {
        TODO("Not yet implemented")
    }

    override fun visit(child: CommonJoin, args: Any?, ctx: CommonInfo): List<String> {
        TODO("Not yet implemented")
    }
}

fun AJoin.collectNames(info: CommonInfo): Map<String, List<JcField>> = this.accept(joinCollectNamesVisitor, null, info)
private val joinCollectNamesVisitor = object : IJoinVisitor<Map<String, List<JcField>>, CommonInfo, Any?> {
    override fun visit(child: CrossJoin, args: Any?, ctx: CommonInfo): Map<String, List<JcField>>? {
        TODO("Not yet implemented")
    }

    override fun visit(child: JpaCollectionJoin, args: Any?, ctx: CommonInfo): Map<String, List<JcField>>? {
        TODO("Not yet implemented")
    }

    override fun visit(child: CommonJoin, args: Any?, ctx: CommonInfo) = child.getNames(ctx)
}


fun AJoin.genJoin(ctx: MethodCtx, name: String, root: JcLocalVar): JcLocalVar =
    this.accept(joinGenJoinVisitor, name to root, ctx)
private val joinGenJoinVisitor = object : IJoinVisitor<JcLocalVar, MethodCtx, Pair<String, JcLocalVar>> {
    override fun visit(child: CrossJoin, args: Pair<String, JcLocalVar>, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: JpaCollectionJoin, args: Pair<String, JcLocalVar>, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun visit(child: CommonJoin, args: Pair<String, JcLocalVar>, ctx: MethodCtx) =
        child.generateJoin(ctx, args.first, args.second)
}

fun AJoin.getLambdas(info: CommonInfo): List<JcMethod> = this.accept(joinGetLambdasVisitor, null, info)
private val joinGetLambdasVisitor = object : IJoinVisitor<List<JcMethod>, CommonInfo, Any?> {
    override fun visit(child: CrossJoin, args: Any?, ctx: CommonInfo): List<JcMethod> {
        TODO("Not yet implemented")
    }

    override fun visit(child: JpaCollectionJoin, args: Any?, ctx: CommonInfo): List<JcMethod> {
        TODO("Not yet implemented")
    }

    override fun visit(child: CommonJoin, args: Any?, ctx: CommonInfo) = child.lambdas(ctx)
}
