package machine.interpreter.transformers.springjpa

import kotlinx.collections.immutable.toPersistentList
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
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.usvm.jvm.util.genericTypes
import org.usvm.jvm.util.isVoid
import org.usvm.jvm.util.toJcType
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer
import org.usvm.machine.interpreter.transformers.springjpa.Select
import util.getTableName


object JcRepositoryTransformer : JcClassExtFeature {

    private val visitedCtx: MutableMap<String, Select> = mutableMapOf()

    private fun visitedName(method: JcMethod): String {
        return "${method.enclosingClass.name}.${method.name}"
    }

    fun addCtx(method: JcMethod, ctx: Select) {
        visitedCtx[visitedName(method)] = ctx
    }

    fun getCtx(method: JcMethod): Select? {
        return visitedCtx[visitedName(method)]
    }

    override fun methodsOf(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod>? {

        if (!clazz.isJpaRepository) return null

        val lambdas = originalMethods.flatMap {
            val query = it.query // TODO: by name
            if (query == null) {
                listOf()
            } else {
                val queryCtx = HqlLexer(CharStreams.fromString(query))
                    .let { CommonTokenStream(it) }
                    .let { HqlParser(it) }
                    .statement()

                val parserRes = JPAQueryVisitor().visit(queryCtx) as Select
                addCtx(it, parserRes)

                val repo = it.enclosingClass

                parserRes.getLambdas(repo.classpath, repo, it)
            }
        }

        return originalMethods.toPersistentList().addAll(lambdas)
    }
}

object JcRepositoryQueryTransformer : JcBodyFillerFeature() {

    override fun condition(method: JcMethod): Boolean {
        return !method.repositoryLambda && method.enclosingClass.isJpaRepository && method.query != null
    }

    override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {

        val repo = method.enclosingClass
        val cp = repo.classpath

        val parserRes = JcRepositoryTransformer.getCtx(method)!! // TODO: query by name
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

    override fun condition(method: JcMethod): Boolean {
        return method.isCrud && method.enclosingClass.isJpaRepository && method.query == null
    }

    override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {

        val repo = method.enclosingClass
        val cp = repo.classpath
        val clazz = cp.findClass(repo.signature!!.genericTypes[0])

        if (method.isSaveUpdDel) {
            generateSaveUpdDel(cp, method, clazz)
            return
        }

        val tbl = generateGlobalTable(cp, "tbl", getTableName(clazz))

        val serializer = clazz.declaredMethods.single { it.generatedStaticSerializer }
        val serLmbd = generateLambda(cp, "serilizer", serializer)

        val deserializer = clazz.declaredMethods.single { it.generatedStaticFetchInit }
        val desLmbd = generateLambda(cp, "deserilizer", deserializer)

        val genType = if (!method.isVoid) {
            val classType = cp.findType(JAVA_CLASS)
            val generic = method.signature?.genericTypes?.single()?.let { cp.findType(it) }
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
