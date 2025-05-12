package machine.interpreter.transformers.springjpa.query

import machine.interpreter.transformers.springjpa.DATABASE_UTILS
import machine.interpreter.transformers.springjpa.DISTINCT_TABLE
import machine.interpreter.transformers.springjpa.FILTER_TABLE
import machine.interpreter.transformers.springjpa.FLAT_TABLE
import machine.interpreter.transformers.springjpa.ITABLE
import machine.interpreter.transformers.springjpa.IWRAPPER
import machine.interpreter.transformers.springjpa.JAVA_BIG_DECIMAL
import machine.interpreter.transformers.springjpa.JAVA_BIG_INT
import machine.interpreter.transformers.springjpa.JAVA_BOOL
import machine.interpreter.transformers.springjpa.JAVA_CLASS
import machine.interpreter.transformers.springjpa.JAVA_DOUBLE
import machine.interpreter.transformers.springjpa.JAVA_FLOAT
import machine.interpreter.transformers.springjpa.JAVA_INTEGER
import machine.interpreter.transformers.springjpa.JAVA_LONG
import machine.interpreter.transformers.springjpa.JAVA_STRING
import machine.interpreter.transformers.springjpa.JOIN_TABLE
import machine.interpreter.transformers.springjpa.LIST_WRAPPER
import machine.interpreter.transformers.springjpa.MAP_TABLE
import machine.interpreter.transformers.springjpa.PAGE_IMPL_WRAPPER
import machine.interpreter.transformers.springjpa.PAGE_WRAPPER
import machine.interpreter.transformers.springjpa.SET_WRAPPER
import machine.interpreter.transformers.springjpa.SINGLETON_TABLE
import machine.interpreter.transformers.springjpa.SORTED_TABLE
import machine.interpreter.transformers.springjpa.generateObjectArray
import machine.interpreter.transformers.springjpa.generateStaticCall
import machine.interpreter.transformers.springjpa.generatedStaticFetchInit
import machine.interpreter.transformers.springjpa.isGeneratedGetter
import machine.interpreter.transformers.springjpa.methodRef
import machine.interpreter.transformers.springjpa.parameterName
import machine.interpreter.transformers.springjpa.putArgumentsToArray
import machine.interpreter.transformers.springjpa.query.path.Path
import machine.interpreter.transformers.springjpa.staticMethodRef
import machine.interpreter.transformers.springjpa.toArgument
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcClassConstant
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcStaticCallExpr
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.usvm.jvm.util.genericTypesFromSignature
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer
import util.database.JcTableInfoCollector

data class CommonInfo(
    val cp: JcClasspath,
    val query: Query,
    val repo: JcClassOrInterface,
    val method: JcMethod,
    val origMethod: JcMethod
) {
    val collector: JcTableInfoCollector
        get() {
            return JcTableInfoCollector(cp).also {
                val dataClass = cp.findClass(repo.signature!!.genericTypesFromSignature[0])
                it.collectTable(dataClass)
                it.dropNotOrigFields()
            }
        }

    val names = NamesManager(method)

    val origMethodArguments = origMethod.parameters.mapIndexed { ix, p -> p.parameterName to ix }.toMap()
    val origReturnGeneric = origMethod.signature?.let { it.genericTypesFromSignature[0] } ?: origMethod.returnType.typeName

    val aliases = query.collectAliases(this) // alias to full name
    val positions = query.collectRowPositions(this) // Foo.bar <-> (columnName <-> origField and index in row)

    val comparerName = "comparer"
    val valueOfName = "valueOf"

    val wrapperType = cp.findType(IWRAPPER) as JcClassType
    val pageType = cp.findType(PAGE_WRAPPER) as JcClassType
    val pageImplType = cp.findType(PAGE_IMPL_WRAPPER) as JcClassType
    val setType = cp.findType(SET_WRAPPER) as JcClassType
    val listType = cp.findType(LIST_WRAPPER) as JcClassType
    val tableType = cp.findType(ITABLE) as JcClassType
    val mapperType = cp.findType(MAP_TABLE) as JcClassType
    val filterType = cp.findType(FILTER_TABLE) as JcClassType
    val distinctType = cp.findType(DISTINCT_TABLE) as JcClassType
    val orderType = cp.findType(SORTED_TABLE) as JcClassType
    val joinType = cp.findType(JOIN_TABLE) as JcClassType
    val flatType = cp.findType(FLAT_TABLE) as JcClassType
    val singletonType = cp.findType(SINGLETON_TABLE) as JcClassType
    val utilsType = cp.findType(DATABASE_UTILS) as JcClassType

    val boolType = cp.findType(JAVA_BOOL) as JcClassType
    val integerType = cp.findType(JAVA_INTEGER) as JcClassType
    val longType = cp.findType(JAVA_LONG) as JcClassType
    val floatType = cp.findType(JAVA_FLOAT) as JcClassType
    val doubleType = cp.findType(JAVA_DOUBLE)
    val strType = cp.findType(JAVA_STRING) as JcClassType
    val bigIntType = cp.findType(JAVA_BIG_INT) as JcClassType
    val bigDecimalType = cp.findType(JAVA_BIG_DECIMAL) as JcClassType
    val byteArrType = cp.arrayTypeOf(cp.byte, false, listOf()) // TODO: check nullability = false
    val objectArrType = cp.arrayTypeOf(cp.objectType, false, listOf())
    val classType = cp.findType(JAVA_CLASS)

    val jcTrue = JcBool(true, cp.boolean)
    val jcFalse = JcBool(false, cp.boolean)
}

class NamesManager(val method: JcMethod) {
    var namesCounter = 0

    fun getLambdaName(): String {
        return "\$lambda#${method.name}#${namesCounter++}"
    }

    fun getMethodName(): String {
        return "\$method#${method.name}#${namesCounter++}"
    }

    fun getPredicateName(): String {
        return "\$predicate#${method.name}#${namesCounter++}"
    }

    fun getVarName(): String {
        return "\$var#${method.name}#${namesCounter++}"
    }

    fun getQueryName(): String {
        return "\$tblName#${method.name}#${namesCounter++}"
    }

}

class MethodCtx(
    val cp: JcClasspath,
    query: Query,
    repo: JcClassOrInterface,
    val method: JcMethod,
    origMethod: JcMethod,
    val genCtx: JcSingleInstructionTransformer.BlockGenerationContext
) {

    constructor(info: CommonInfo, genCtx: JcSingleInstructionTransformer.BlockGenerationContext)
            : this(info.cp, info.query, info.repo, info.method, info.origMethod, genCtx)

    val common = CommonInfo(cp, query, repo, method, origMethod)
    val names = common.names

    private var methodArgs: JcLocalVar? = null
    fun getMethodArgs(): JcLocalVar {
        methodArgs?.also { return it }
        methodArgs = genCtx.putArgumentsToArray(cp, "methodArgs", method)
        return methodArgs!!
    }

    fun columns(path: Path): Map<String, Pair<JcField, Int>> {
        val fullName = path.applyAliases(common)
        return common.positions[fullName]!!
    }

    fun applyAliases(alias: String) = common.aliases.getOrDefault(alias, alias)

    fun getLambdaName() = names.getLambdaName()

    fun getMethodName() = names.getMethodName()

    fun getVarName() = names.getVarName()

    fun getPredicateName() = names.getPredicateName()

    fun newVar(type: JcType) = genCtx.nextLocalVar(common.names.getVarName(), type)

    fun typeConst(type: JcType) = JcClassConstant(type, common.classType)

    fun genStaticCall(name: String, methodName: String, args: List<JcValue>) =
        genCtx.generateStaticCall(name, methodName, common.utilsType, args)

    private var currObj: JcLocalVar? = null
    fun genObj(name: String): JcLocalVar {
        currObj?.also { return it }

        val aliased = applyAliases(name)
        val columns = common.positions[aliased]!!.values

        val newRow = genCtx.generateObjectArray(cp, common.names.getVarName(), columns.count())

        val row = common.method.parameters.first().toArgument
        columns.forEachIndexed { ix, (_, oldIx) ->
            val elem = JcArrayAccess(newRow, JcInt(ix, cp.int), cp.objectType)
            val value = JcArrayAccess(row, JcInt(oldIx, cp.int), cp.objectType)
            genCtx.addInstruction { loc -> JcAssignInst(loc, elem, value) }
        }

        val origClass = common.collector.getTableByPartName(aliased).single().origClass

        val res = newVar(origClass.toType())
        val statInit = origClass.toType().declaredMethods.single { it.method.generatedStaticFetchInit }
        val call = JcStaticCallExpr(statInit.staticMethodRef, listOf(newRow))
        genCtx.addInstruction { loc -> JcAssignInst(loc, res, call) }

        currObj = res
        return res
    }

    fun genField(root: String, fields: List<String>) = genComplexField(root, fields)

    private fun genComplexField(root: String, fields: List<String>): JcLocalVar {
        val obj = genObj(root)
        return fields.fold(obj) { acc, fieldName ->
            val classType = acc.type as JcClassType
            val getter = classType.declaredMethods.single { it.method.isGeneratedGetter(fieldName) }
            val v = newVar(getter.returnType)
            val call = JcSpecialCallExpr(getter.methodRef, acc, listOf())
            genCtx.addInstruction { loc -> JcAssignInst(loc, v, call) }
            v
        }
    }
}
