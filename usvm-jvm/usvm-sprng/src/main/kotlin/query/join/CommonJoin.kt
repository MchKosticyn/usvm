package machine.interpreter.transformers.springjpa.query.join

import machine.interpreter.transformers.springjpa.DATA_ROW
import machine.interpreter.transformers.springjpa.ITABLE
import machine.interpreter.transformers.springjpa.JcBodyFillerFeature
import machine.interpreter.transformers.springjpa.JcMethodBuilder
import machine.interpreter.transformers.springjpa.REPOSITORY_LAMBDA
import machine.interpreter.transformers.springjpa.generateCast
import machine.interpreter.transformers.springjpa.generateDataRowOf
import machine.interpreter.transformers.springjpa.generateGlobalTableAccess
import machine.interpreter.transformers.springjpa.generateLambda
import machine.interpreter.transformers.springjpa.generateNewWithInit
import machine.interpreter.transformers.springjpa.generateStaticCall
import machine.interpreter.transformers.springjpa.generateVirtualCall
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expresion.Expression
import machine.interpreter.transformers.springjpa.query.expresion.LNull
import machine.interpreter.transformers.springjpa.query.path.Path
import machine.interpreter.transformers.springjpa.repositoryLambda
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcStringConstant
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.jvmName
import org.objectweb.asm.Opcodes
import org.usvm.jvm.util.genericTypesFromSignature
import org.usvm.jvm.util.stringType
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer

abstract class CommonJoin(
    val target: Path,
    val pred: Expression?
) : Join() {

    abstract fun getJoinArgs(
        ctx: MethodCtx,
        leftTbl: JcLocalVar,
        rightTbl: JcLocalVar,
        onMethod: JcLocalVar
    ): List<JcValue>

    override fun getAlias(): Pair<String, String>? = target.getAlias()

    override fun positions(info: CommonInfo): List<String> {
        TODO("Not yet implemented")
    }

    override fun collectNames(info: CommonInfo): Map<String, List<JcField>> = with(info) {
        val name = target.applyAliases(info)
        val path = target.root.path // TODO: variants with other types of paths
        fun alias(n: String) = aliases.getOrDefault(n, n)

        val rootClassName = collector.getTableByPartName(alias(path.root)).single().origClassName
        val rootClass = cp.findClass(rootClassName)
        val targetClass = path.cont.fold(rootClass) { clazz, fieldName ->
            val field = clazz.declaredFields.single { it.name == fieldName }
            val type = field.signature?.let { it.genericTypesFromSignature[0] } ?: field.type.typeName
            cp.findClass(type)
        }
        val columns = collector.collectTable(targetClass).origFieldsInOrder(cp)
        return mapOf(name to columns)
    }

    override fun getLambdas(info: CommonInfo): List<JcMethod> {
        val predicateLambdas = pred?.let {
            it.getLambdas(info) + getOnMethod(info)
        } ?: emptyList()

        return predicateLambdas + getMapperMethod(info)
    }

    private fun getOnMethod(info: CommonInfo) = pred!!.toLambda(info)

    fun genOnMethod(ctx: MethodCtx): JcLocalVar {
        if (pred != null) {
            val method = getOnMethod(ctx.common)
            return ctx.genCtx.generateLambda(ctx.cp, "on#${ctx.getMethodName()}", method)
        }

        return LNull().genInst(ctx)
    }

    override fun genJoin(ctx: MethodCtx, name: String, root: JcLocalVar) =
        if (target.isSimple()) resolveSimpleTarget(ctx, name, root) else resolveComplexTarget(ctx, name, root)

    private fun resolveSimpleTarget(ctx: MethodCtx, name: String, root: JcLocalVar): JcLocalVar = with(ctx) {
        val targetName = target.applyAliases(common)
        val targetTbl = common.collector.getTableByPartName(targetName).single() // it must be single

        val origClass = cp.findClass(targetTbl.origClassName)
        val tbl = genCtx.generateGlobalTableAccess(cp, getVarName(), targetTbl.name, origClass)
        val onMethod = genOnMethod(ctx)

        val args = getJoinArgs(ctx, root, tbl, onMethod)

        return genCtx.generateNewWithInit(name, common.joinType, args)
    }

    // ... JOIN foo.bar
    // FlatTable(
    //  MapTable( root,
    //      row ->
    //          val foo = [buildFoo] row
    //          val target = foo.$getBar.unwrap()
    //          val root = SingletonTable(foo, foo.class)
    //          JoinTable(root, target, Foo.$ser, Bar.$ser)
    //  )
    // )
    private fun resolveComplexTarget(ctx: MethodCtx, name: String, root: JcLocalVar): JcLocalVar {
        val mapper = getMapperMethod(ctx.common)
        val mapperLambda = ctx.genCtx.generateLambda(ctx.cp, "flt_map#${ctx.getLambdaName()}", mapper)
        val mapArgs = listOf(root, mapperLambda)
        val mapped = ctx.genCtx.generateNewWithInit("join#${ctx.getVarName()}", ctx.common.mapperType, mapArgs)

        val flatArgs = listOf(mapped)
        val flat = ctx.genCtx.generateNewWithInit(name, ctx.common.flatType, flatArgs)

        return flat
    }

    var mapper: JcMethod? = null
    private fun getMapperMethod(info: CommonInfo): JcMethod {
        mapper?.also { return it }
        val methodName = info.names.getMethodName()
        val itableDesc = "${ITABLE.jvmName().dropLast(1)}<${DATA_ROW.jvmName()}>;"
        val sig = "(${DATA_ROW.jvmName()})${itableDesc}"
        val method = JcMethodBuilder(info.repo)
            .setName(methodName)
            .setSig(sig)
            .setRetType(ITABLE)
            .setAccess(Opcodes.ACC_STATIC)
            .addBlanckAnnot(REPOSITORY_LAMBDA)
            .addFreshParam(DATA_ROW)
            .addFillerFuture(FlatFeature(info, methodName, this))
            .buildMethod()
        mapper = method
        return method
    }

    class FlatFeature(val info: CommonInfo, val name: String, val join: CommonJoin) : JcBodyFillerFeature() {
        override fun condition(method: JcMethod) = method.repositoryLambda && method.name == name

        //  row ->
        //      val foo = [buildFoo] row
        //      val root = SingletonTable(foo, foo.class)
        //      val target = foo.$getBar.unwrap()
        //      return JoinTable(root, target, Foo.$ser, Bar.$ser)
        override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) =
            with(MethodCtx(info.cp, info.query, info.repo, method, info.origMethod, this)) {
                val targetRoot = join.target.root.path.root // TODO: optimize
                val targetCont = join.target.root.path.cont

                val obj = genObj(targetRoot)
                val root = genCtx.generateStaticCall(
                    "root",
                    "of",
                    info.singletonType,
                    listOf(JcStringConstant(targetRoot, cp.stringType), obj)
                )

                val field = genField(targetRoot, targetCont)
                val castedField = genCtx.generateCast("cont_upcast", field, common.wrapperType)
                val unwrapped = genCtx.generateVirtualCall(
                    "cont_unwrapp",
                    "unwrap",
                    common.wrapperType,
                    castedField,
                    emptyList()
                )

                val entityAlias = join.getAlias()?.first ?: common.names.getVarName()
                val dataRowed = genCtx.generateDataRowOf(cp, "unwrapped", entityAlias, unwrapped)

                val onMethod = join.genOnMethod(this)
                val args = join.getJoinArgs(this, root, dataRowed, onMethod)
                val join = genCtx.generateNewWithInit("join", info.joinType, args)
                addInstruction { loc -> JcReturnInst(loc, join) }
            }
    }
}

class InnerJoin(
    target: Path,
    pred: Expression?
) : CommonJoin(target, pred) {
    override fun getJoinArgs(
        ctx: MethodCtx,
        leftTbl: JcLocalVar,
        rightTbl: JcLocalVar,
        onMethod: JcLocalVar
    ): List<JcValue> {
        val isLeft = JcBool(false, ctx.cp.boolean)
        return listOf(leftTbl, rightTbl, onMethod, isLeft)
    }
}

class FullJoin(
    target: Path,
    pred: Expression?
) : CommonJoin(target, pred) {
    override fun getJoinArgs(
        ctx: MethodCtx,
        leftTbl: JcLocalVar,
        rightTbl: JcLocalVar,
        onMethod: JcLocalVar
    ): List<JcValue> {
        TODO("Not yet implemented")
    }
}

class RightJoin(
    target: Path,
    pred: Expression?
) : CommonJoin(target, pred) {
    override fun getJoinArgs(
        ctx: MethodCtx,
        leftTbl: JcLocalVar,
        rightTbl: JcLocalVar,
        onMethod: JcLocalVar
    ): List<JcValue> {
        val isLeft = JcBool(false, ctx.cp.boolean)
        return listOf(rightTbl, leftTbl, onMethod, isLeft)
    }
}

class LeftJoin(
    target: Path,
    pred: Expression?
) : CommonJoin(target, pred) {
    override fun getJoinArgs(
        ctx: MethodCtx,
        leftTbl: JcLocalVar,
        rightTbl: JcLocalVar,
        onMethod: JcLocalVar
    ): List<JcValue> {
        val isLeft = JcBool(true, ctx.cp.boolean)
        return listOf(leftTbl, rightTbl, onMethod, isLeft)
    }
}
