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
import org.jacodb.api.jvm.ext.allSuperHierarchySequence
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.approximation.JcEnrichedVirtualParameter
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.jacodb.impl.features.JcFeaturesChain
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethod
import org.jacodb.impl.features.classpaths.virtual.JcVirtualMethodImpl
import org.jacodb.impl.features.classpaths.virtual.JcVirtualParameter
import org.usvm.jvm.util.genericTypesFromSignature
import org.usvm.jvm.util.isVoid
import org.usvm.jvm.util.toJcType
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer
import org.usvm.machine.interpreter.transformers.springjpa.Select
import util.database.IdColumnInfo
import util.database.JcTableInfoCollector
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

    private fun addCtx(method: JcMethod, ctx: Select) {
        visitedCtx[visitedName(method)] = ctx
    }

    fun getCtx(method: JcMethod) = visitedCtx[visitedName(method)]

    private fun collectRepositoryMethods(clazz: JcClassOrInterface, originalMethods: List<JcMethod>) =
        clazz.allSuperHierarchySequence.filter { it.isJpaRepository }
            .flatMapTo(originalMethods.toMutableList()) { it.declaredMethods }
            .map { JcJpaMethod.of(it, clazz) }

    override fun methodsOf(clazz: JcClassOrInterface, originalMethods: List<JcMethod>): List<JcMethod>? {

        // Remember to call bindMachineOptions!!!
        if (!clazz.isJpaRepository || !machineOptions!!.isProjectLocation(clazz)) return null

        val dataClass = clazz.signature!!.genericTypesFromSignature.first().let { clazz.classpath.findClass(it) }

        val methods = collectRepositoryMethods(clazz, originalMethods)
        val lambdas = methods.flatMap {
            if (it.query == null && it.isCrud)
                return@flatMap emptyList<JcMethod>()

            if (it.query == null) return@flatMap emptyList()
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

        return methods + lambdas
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

private val crudNames = listOf(
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

// TODO: saveAll, deleteAll, deleteAllById
class JcRepositoryCrudTransformer(
    val collector: JcTableInfoCollector
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) =
        !method.repositoryLambda
                && method.query == null
                && method.enclosingClass.isJpaRepository
                && method.isCrud

    override fun JcSingleInstructionTransformer.BlockGenerationContext.generateBody(method: JcMethod) {
        val repo = method.enclosingClass
        val cp = repo.classpath
        val clazz = cp.findClass(repo.signature!!.genericTypesFromSignature[0])
        val classTable = collector.getTable(clazz)!!

        if (method.isSaveUpdDel) {
            generateSaveUpdDel(cp, method, clazz)
            return
        }

        val manager = generateManagerAccessWithInit(cp, "tbl", getTableName(clazz), clazz)

        val complexIdTranslator = if (classTable.idColumn is IdColumnInfo.SingleId) JcNullConstant(cp.objectType)
        else {
            val complexIdClassName = classTable.getComplexIdClassName()!!
            val complexId = cp.findType(complexIdClassName) as JcClassType
            val buildIdsMethod = complexId.declaredMethods.single { it.method.generatedBuildIds }.method
            generateLambda(cp, "complexIdFieldTranslator", buildIdsMethod)
        }

        val crudTyp = cp.findType(CRUD_MANAGER) as JcClassType
        val crudManager = generateNewWithInit("crud", crudTyp, listOf(manager, complexIdTranslator))

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
        val obj = method.parameters.first().toArgument.let {
            generateCast("obj_cast", it, clazz.toType())
        }
        val methodName = if (method.name == "save") SAVE_UPDATE_NAME else DELETE_NAME
        generateVoidStaticCall(methodName, clazz.toType(), listOf(obj, ctx))
        addInstruction { loc -> JcReturnInst(loc, null) }
    }
}
