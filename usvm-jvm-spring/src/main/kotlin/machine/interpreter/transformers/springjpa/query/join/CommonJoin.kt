package machine.interpreter.transformers.springjpa.query.join

import machine.interpreter.transformers.springjpa.ITABLE
import machine.interpreter.transformers.springjpa.JAVA_OBJ_ARR
import machine.interpreter.transformers.springjpa.JcBodyFillerFeature
import machine.interpreter.transformers.springjpa.JcMethodBuilder
import machine.interpreter.transformers.springjpa.REPOSITORY_LAMBDA
import machine.interpreter.transformers.springjpa.generateGlobalTableAccess
import machine.interpreter.transformers.springjpa.generateLambda
import machine.interpreter.transformers.springjpa.generateNewWithInit
import machine.interpreter.transformers.springjpa.generatedStaticSerializer
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import machine.interpreter.transformers.springjpa.query.expresion.Expression
import machine.interpreter.transformers.springjpa.query.expresion.LNull
import machine.interpreter.transformers.springjpa.query.path.Path
import machine.interpreter.transformers.springjpa.repositoryLambda
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.jvmName
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.objectweb.asm.Opcodes
import org.usvm.jvm.util.genericTypesFromSignature
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer

abstract class CommonJoin(
    val target: Path,
    val pred: Expression?
): Join() {

    abstract fun getJoinArgs(
        ctx: MethodCtx,
        leftTbl: JcLocalVar,
        rightTbl: JcLocalVar,
        leftSerializer: JcLocalVar,
        rightSerializer: JcLocalVar,
        onMethod: JcLocalVar
    ): List<JcValue>

    override fun getAlias(): Pair<String, String>? = target.getAlias()

    override fun positions(info: CommonInfo): List<String> {
        TODO("Not yet implemented")
    }

    override fun collectNames(info: CommonInfo): Map<String, List<JcField>> {
        val name = target.applyAliases(info)
        val path = target.root.path // TODO: variants with other types of paths
        fun alias(n: String): String {
            return info.aliases.getOrDefault(n, n)
        }

        val rootClass = info.collector.getTableByPartName(alias(path.root)).single().origClass
        val targetClass = path.cont.fold(rootClass) { clazz, fieldName ->
            val field = clazz.declaredFields.single { it.name == fieldName }
            val type = field.signature?.let { it.genericTypesFromSignature[0] } ?: field.type.typeName
            info.cp.findClass(type)
        }
        val columns = info.collector.collectTable(targetClass).origFieldsInOrder()
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

    private fun resolveSimpleTarget(ctx: MethodCtx, name: String, root: JcLocalVar): JcLocalVar {
        val targetName = target.applyAliases(ctx.common)
        val targetTbl = ctx.common.collector.getTableByPartName(targetName).single() // it must be single

        val tbl = ctx.genCtx.generateGlobalTableAccess(ctx.cp, ctx.getVarName(), targetTbl.name, targetTbl.origClass)

        val serializer = targetTbl.origClass.declaredMethods.single { it.generatedStaticSerializer }
        val ser = ctx.genCtx.generateLambda(ctx.cp, ctx.getLambdaName(), serializer)

        val onMethod = genOnMethod(ctx)
        val leftSerializer = LNull().genInst(ctx)
        val args = getJoinArgs(ctx, root, tbl, leftSerializer, ser, onMethod)

        return ctx.genCtx.generateNewWithInit(name, ctx.common.joinType, args)
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
        val mapArgs = listOf(root, mapperLambda, ctx.typeConst(ctx.common.tableType))
        val mapped = ctx.genCtx.generateNewWithInit("join#${ctx.getVarName()}", ctx.common.mapperType, mapArgs)

        val flatArgs = listOf(mapped, ctx.typeConst(ctx.common.objectArrType))
        val flat = ctx.genCtx.generateNewWithInit(name, ctx.common.flatType, flatArgs)

        return flat
    }

    var mapper: JcMethod? = null
    private fun getMapperMethod(info: CommonInfo): JcMethod {
        mapper?.also { return it }
        val methodName = info.names.getMethodName()
        val itableDesc = "${ITABLE.jvmName().dropLast(1)}<${JAVA_OBJ_ARR.jvmName()}>;"
        val sig = "(${JAVA_OBJ_ARR.jvmName()})${itableDesc}"
        val method = JcMethodBuilder(info.repo)
            .setName(methodName)
            .setSig(sig)
            .setRetType(ITABLE)
            .setAccess(Opcodes.ACC_STATIC)
            .addBlanckAnnot(REPOSITORY_LAMBDA)
            .addFreshParam(JAVA_OBJ_ARR)
            .addFillerFuture(FlatFeature(info, methodName, this))
            .buildMethod()
        mapper = method
        return method
    }

    class FlatFeature(val info: CommonInfo, val name: String, val join: CommonJoin) : JcBodyFillerFeature() {
        override fun condition(method: JcMethod): Boolean {
            return method.repositoryLambda && method.name == name
        }

        //  row ->
        //      val foo = [buildFoo] row
        //      val root = SingletonTable(foo, foo.class)
        //      val target = foo.$getBar.unwrap()
        //      return JoinTable(root, target, Foo.$ser, Bar.$ser)
        override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {
            val ctx = MethodCtx(info.cp, info.query, info.repo, method, info.origMethod, this)
            val targetRoot = join.target.root.path.root // TODO: optimize
            val targetCont = join.target.root.path.cont

            val obj = ctx.genObj(targetRoot)
            val objType = ctx.typeConst(obj.type)
            val root = ctx.genCtx.generateNewWithInit("root", info.singletonType, listOf(obj, objType))

            val fieldTbl = ctx.newVar(info.tableType)
            val field = ctx.genField(targetRoot, targetCont)
            val unwrap = info.wrapperType.declaredMethods.single { it.name == "unwrap" }.let {
                VirtualMethodRefImpl.of(field.type as JcClassType, it)
            }
            val unwrapCall = JcVirtualCallExpr(unwrap, field, listOf())
            ctx.genCtx.addInstruction { loc -> JcAssignInst(loc, fieldTbl, unwrapCall) }

            val rootSer = (obj.type as JcClassType).declaredMethods.single { it.method.generatedStaticSerializer }
                .let { ctx.genCtx.generateLambda(ctx.cp, "root_serializer", it.method) }
            val targetSer = field.typeName
                .substringAfter("<").substringBefore(">")
                .let { ctx.cp.findType(it) as JcClassType }
                .declaredMethods.single { it.method.generatedStaticSerializer }
                .let { ctx.genCtx.generateLambda(ctx.cp, "target_serializer", it.method) }
            val onMethod = join.genOnMethod(ctx)
            val args = join.getJoinArgs(ctx, root, fieldTbl, rootSer, targetSer, onMethod)
            val join = ctx.genCtx.generateNewWithInit("join", info.joinType, args)
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
        leftSerializer: JcLocalVar,
        rightSerializer: JcLocalVar,
        onMethod: JcLocalVar
    ): List<JcValue> {
        val rightSize = JcInt(-1, ctx.cp.int)
        val isLeft = JcBool(false, ctx.cp.boolean)
        return listOf(leftTbl, rightTbl, leftSerializer, rightSerializer, rightSize, onMethod, isLeft)
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
        leftSerializer: JcLocalVar,
        rightSerializer: JcLocalVar,
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
        leftSerializer: JcLocalVar,
        rightSerializer: JcLocalVar,
        onMethod: JcLocalVar
    ): List<JcValue> {
        val rightSize = JcInt(ctx.columns(target).size, ctx.cp.int)
        val isLeft = JcBool(false, ctx.cp.boolean)
        return listOf(rightTbl, leftTbl, rightSerializer, leftSerializer, rightSize, onMethod, isLeft)
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
        leftSerializer: JcLocalVar,
        rightSerializer: JcLocalVar,
        onMethod: JcLocalVar
    ): List<JcValue> {
        val rightSize = JcInt(-1, ctx.cp.int)
        val isLeft = JcBool(true, ctx.cp.boolean)
        return listOf(leftTbl, rightTbl, leftSerializer, rightSerializer, rightSize, onMethod, isLeft)
    }
}
