package machine.interpreter.transformers.springjpa

import kotlinx.collections.immutable.toPersistentList
import machine.JcConcreteMachineOptions
import machine.interpreter.transformers.springjpa.query.JPANameTranslator
import machine.interpreter.transformers.springjpa.query.JPAQueryVisitor
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.hibernate.grammars.hql.HqlLexer
import org.hibernate.grammars.hql.HqlParser
import org.jacodb.api.jvm.JcClassExtFeature
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcClassConstant
import org.jacodb.api.jvm.cfg.JcNullConstant
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.JAVA_OBJECT
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.usvm.jvm.util.genericTypesFromSignature
import org.usvm.jvm.util.isVoid
import org.usvm.jvm.util.toJcType
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer
import org.usvm.machine.interpreter.transformers.springjpa.Select
import util.database.getTableName


object JcRepositoryTransformer : JcClassExtFeature {

    // Remember to call bindMachineOptions!!!
    private var machineOptions: JcConcreteMachineOptions? = null

    private val visitedCtx: MutableMap<String, Select> = mutableMapOf()

    private fun visitedName(method: JcMethod): String {
        return "${method.enclosingClass.name}.${method.name}"
    }

    fun bindMachineOptions(options: JcConcreteMachineOptions) {
        machineOptions = options
    }

    private fun addCtx(method: JcMethod, ctx: Select) { visitedCtx[visitedName(method)] = ctx }
    fun getCtx(method: JcMethod) = visitedCtx[visitedName(method)]

    // TODO: may failed
    private fun collectRepositoryMethods(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod> {
        val superClass = clazz.superClass
        return if (superClass == null || !superClass.isJpaRepository) originalMethods
        else {
            val newMethods = originalMethods.toPersistentList().addAll(superClass.declaredMethods)
            collectRepositoryMethods(superClass, newMethods)
        }

    }

    override fun methodsOf(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod>? {

        // Remember to call bindMachineOptions!!!
        if (!clazz.isJpaRepository || !machineOptions!!.isProjectLocation(clazz)) return null

        val dataClass = clazz.signature!!.genericTypesFromSignature.first().let { clazz.classpath.findClass(it) }

        val methods = collectRepositoryMethods(clazz, originalMethods)
        val lambdas = methods.flatMap {
            if (it.query == null && JcRepositoryCrudTransformer.crudNames.contains(it.name))
                return@flatMap emptyList<JcMethod>()

            val query = it.query ?: JPANameTranslator(it.name, dataClass).buildQuery()
            val queryCtx = HqlLexer(CharStreams.fromString(query))
                .let(::CommonTokenStream)
                .let(::HqlParser)
                .statement()

            val parserRes = JPAQueryVisitor().visit(queryCtx) as Select
            addCtx(it, parserRes)

            val repo = it.enclosingClass

            parserRes.getLambdas(repo.classpath, repo, it)
        }

        return originalMethods.toPersistentList().addAll(lambdas)
    }
}

object JcRepositoryQueryTransformer : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = method.query != null

    override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {

        val repo = method.enclosingClass
        val cp = repo.classpath

        val parserRes = JcRepositoryTransformer.getCtx(method)!!
        val res = parserRes.genInst(cp, repo, method, this)
        addInstruction { loc -> JcReturnInst(loc, res) }
    }
}

// TODO: saveAll, deleteAll, deleteAllById
object JcRepositoryCrudTransformer : JcBodyFillerFeature() {

    val crudNames = listOf(
        "save",
        "saveAll",
        "delete",
        "deleteAll",
        "deleteAllById",
        "existById",
        "findAll",
        "findById",
        "findAllById"
    )

    val JcMethod.isCrud: Boolean get() = crudNames.contains(name)
    val JcMethod.isSaveUpdDel: Boolean get() = listOf("save", "delete").contains(name)

    override fun condition(method: JcMethod) =
        !method.repositoryLambda
                && method.query == null
                && method.enclosingClass.isJpaRepository
                && method.isCrud

    override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {

        val repo = method.enclosingClass
        val cp = repo.classpath
        val clazz = cp.findClass(repo.signature!!.genericTypesFromSignature[0])

        if (method.isSaveUpdDel) {
            generateSaveUpdDel(cp, method, clazz)
            return
        }

        val tbl = generateGlobalTableAccess(cp, "tbl", getTableName(clazz), clazz)

        val serializer = clazz.declaredMethods.single { it.generatedStaticSerializer }
        val serLmbd = generateLambda(cp, "serilizer", serializer)

        val deserializer = clazz.declaredMethods.single { it.generatedStaticFetchInit }
        val desLmbd = generateLambda(cp, "deserilizer", deserializer)

        val genType = if (!method.isVoid) {
            val classType = cp.findType(JAVA_CLASS)
            val generic = method.signature?.genericTypesFromSignature?.single()?.let { cp.findType(it) }
                ?: method.returnType.toJcType(cp)!!
            val v = nextLocalVar("generic_type", classType)
            val cl = JcClassConstant(generic, classType)
            addInstruction { loc -> JcAssignInst(loc, v, cl) }
            v

        } else {
            val v = nextLocalVar("generic_type", cp.objectType)
            val n = JcNullConstant(cp.objectType)
            addInstruction { loc -> JcAssignInst(loc, v, n) }
            v
        }

        val crudTyp = cp.findType(CRUD_MANAGER) as JcClassType
        val crudManager = generateNewWithInit("crud", crudTyp, listOf(tbl, serLmbd, desLmbd, genType))

        val methodName = buildString {
            append(method.name)

            if (!method.isVoid) append("_") else return@buildString

            if (method.signature != null) {
                val generic = method.returnType.typeName
                append(generic.replace(".", "_"))
            } else {
                append("T")
            }
        }

        val crudMethod = crudTyp.declaredMethods.single { it.name == methodName }.let {
            VirtualMethodRefImpl.of(crudTyp, it)
        }

        val args = method.parameters.map { it.toArgument }
        val call = JcVirtualCallExpr(crudMethod, crudManager, args)

        if (method.isVoid) {
            addInstruction { loc -> JcCallInst(loc, call) }
            addInstruction { loc -> JcReturnInst(loc, null) }
            return
        }

        val callRes = nextLocalVar("call_res", crudMethod.method.returnType)
        addInstruction { loc -> JcAssignInst(loc, callRes, call) }

        addInstruction { loc -> JcReturnInst(loc, callRes) }
    }

    private fun JcSingleInstructionTransformer.BlockGenerationContext.generateSaveUpdDel(
        cp: JcClasspath,
        method: JcMethod,
        clazz: JcClassOrInterface
    ) {
        val ctxType = cp.findType(SAVE_UPD_DEL_CTX) as JcClassType
        val ctx = generateNewWithInit("ctx", ctxType, listOf())
        val obj = method.parameters.first().toArgument
        val methodName = if (method.name == "save") SAVE_UPDATE_NAME else DELETE_NAME
        generateVoidStaticCall(methodName, clazz.toType(), listOf(obj, ctx))
        addInstruction { loc -> JcReturnInst(loc, null) }
    }
}
