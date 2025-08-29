package machine.interpreter.transformers.springjpa.query

import jpa.AGGREGATORS
import jpa.DATABASE_UTILS
import jpa.DATA_ROW
import jpa.DISTINCT_TABLE
import jpa.FILTER_TABLE
import jpa.FLAT_TABLE
import jpa.GROUP_BY_TABLE
import jpa.HAVING_TABLE
import jpa.ITABLE
import jpa.IWRAPPER
import jpa.JAVA_BIG_DECIMAL
import jpa.JAVA_BIG_INT
import jpa.JAVA_BOOL
import jpa.JAVA_CLASS
import jpa.JAVA_DOUBLE
import jpa.JAVA_FLOAT
import jpa.JAVA_INTEGER
import jpa.JAVA_LOCAL_DATE
import jpa.JAVA_LONG
import jpa.JAVA_STRING
import jpa.JOIN_TABLE
import jpa.JcTableInfoCollector
import jpa.LIST_WRAPPER
import jpa.MAP_TABLE
import jpa.OPTIONAL
import jpa.PAGE_IMPL_WRAPPER
import jpa.PAGE_WRAPPER
import jpa.SET_WRAPPER
import jpa.SINGLETON_TABLE
import jpa.SORTED_TABLE
import jpa.generateCast
import jpa.generateStaticCall
import jpa.generateVirtualCall
import jpa.generatedGetter
import jpa.methodRef
import jpa.parameterName
import jpa.putArgumentsToArray
import jpa.toArgument
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcClassConstant
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcNullConstant
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcStringConstant
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.byte
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.objectType
import org.usvm.jvm.util.genericTypesFromSignature
import org.usvm.jvm.util.stringType
import org.usvm.jvm.util.transformers.JcSingleInstructionTransformer
import org.usvm.spring.query.Query

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
                it.dropNotOrigFields() // see JcTableInfoCollector's doc
            }
        }

    val names = NamesManager(method)

    val origMethodArguments = origMethod.parameters.mapIndexed { ix, p -> p.parameterName to ix }.toMap()
    val origReturnGeneric =
        origMethod.signature?.let { it.genericTypesFromSignature[0] } ?: origMethod.returnType.typeName

    val aliases = query.collectAliases(this) // alias to full name

    val comparerName = "comparer"
    val valueOfName = "valueOf"
    val firstEnsureName = "firstEnsure"
    val getName = "get"

    val wrapperType by lazy { cp.findType(IWRAPPER) as JcClassType }
    val pageType by lazy { cp.findType(PAGE_WRAPPER) as JcClassType }
    val optionalType by lazy { cp.findType(OPTIONAL) as JcClassType }
    val pageImplType by lazy { cp.findType(PAGE_IMPL_WRAPPER) as JcClassType }
    val setType by lazy { cp.findType(SET_WRAPPER) as JcClassType }
    val listType by lazy { cp.findType(LIST_WRAPPER) as JcClassType }
    val tableType by lazy { cp.findType(ITABLE) as JcClassType }
    val mapperType by lazy { cp.findType(MAP_TABLE) as JcClassType }
    val filterType by lazy { cp.findType(FILTER_TABLE) as JcClassType }
    val havingType by lazy { cp.findType(HAVING_TABLE) as JcClassType }
    val distinctType by lazy { cp.findType(DISTINCT_TABLE) as JcClassType }
    val orderType by lazy { cp.findType(SORTED_TABLE) as JcClassType }
    val groupByType by lazy { cp.findType(GROUP_BY_TABLE) as JcClassType }
    val joinType by lazy { cp.findType(JOIN_TABLE) as JcClassType }
    val flatType by lazy { cp.findType(FLAT_TABLE) as JcClassType }
    val singletonType by lazy { cp.findType(SINGLETON_TABLE) as JcClassType }
    val aggregatorsType by lazy { cp.findType(AGGREGATORS) as JcClassType }
    val functionsType by lazy { cp.findType(DATABASE_UTILS) as JcClassType }
    val utilsType by lazy { cp.findType(DATABASE_UTILS) as JcClassType }
    val dataRowType by lazy { cp.findType(DATA_ROW) as JcClassType }

    val boolType by lazy { cp.findType(JAVA_BOOL) as JcClassType }
    val integerType by lazy { cp.findType(JAVA_INTEGER) as JcClassType }
    val longType by lazy { cp.findType(JAVA_LONG) as JcClassType }
    val floatType by lazy { cp.findType(JAVA_FLOAT) as JcClassType }
    val doubleType by lazy { cp.findType(JAVA_DOUBLE) as JcClassType }
    val strType by lazy { cp.findType(JAVA_STRING) as JcClassType }
    val bigIntType by lazy { cp.findType(JAVA_BIG_INT) as JcClassType }
    val bigDecimalType by lazy { cp.findType(JAVA_BIG_DECIMAL) as JcClassType }
    val byteArrType by lazy { cp.arrayTypeOf(cp.byte, false, listOf()) }
    val localDateType by lazy { cp.findType(JAVA_LOCAL_DATE) as JcClassType }
    val objectArrType by lazy { cp.arrayTypeOf(cp.objectType, false, listOf()) }
    val classType by lazy { cp.findType(JAVA_CLASS) }

    val jcTrue by lazy { JcBool(true, cp.boolean) }
    val jcFalse by lazy { JcBool(false, cp.boolean) }
    val jcNull by lazy { JcNullConstant(cp.objectType) }
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

    fun applyAliases(alias: String) = common.aliases.getOrDefault(alias, alias)

    fun getLambdaName() = names.getLambdaName()

    fun getMethodName() = names.getMethodName()

    fun getVarName() = names.getVarName()

    fun getPredicateName() = names.getPredicateName()

    fun newVar(type: JcType) = genCtx.nextLocalVar(common.names.getVarName(), type)

    fun typeConst(type: JcType) = JcClassConstant(type, common.classType)

    // only for Utils calls!
    fun genStaticCall(name: String, methodName: String, args: List<JcValue>) =
        genCtx.generateStaticCall(name, methodName, common.utilsType, args)

    private var currObj: JcLocalVar? = null
    fun genObj(name: String, isGrouped: Boolean = false): JcLocalVar {
        currObj?.also { return it }

        val aliased = applyAliases(name)

        val row = common.method.parameters.first().toArgument
            .let {
                if (!isGrouped) it
                // if query contains GROUP BY then type of chain is ITable<ITable>,
                // so for expressions like book.name, COUNT(book) we need to take
                // ITable.firstEnsure().name, COUNT(ITable)
                // ^ always same for all rows of ITable and always exist
                // because it must be value witch GROUP BY'ed
                else {
                    genCtx.generateVirtualCall(
                        "grouped_first_ensure",
                        common.firstEnsureName,
                        common.tableType,
                        it,
                        emptyList()
                    )
                }
            }

        val res = genCtx.generateVirtualCall(
            "data_row_get_${getVarName()}",
            common.getName,
            common.dataRowType,
            row,
            listOf(JcStringConstant(name, cp.stringType))
        )

        val objType = cp.findType(common.collector.getTableByPartName(aliased).single().origClassName)
        val casted = genCtx.generateCast("cast_${getVarName()}", res, objType)

        currObj = casted
        return casted
    }

    fun genField(root: String, fields: List<String>, isGrouped: Boolean = false) =
        genComplexField(root, fields, isGrouped)

    private fun genComplexField(root: String, fields: List<String>, isGrouped: Boolean = false): JcLocalVar {
        val obj = genObj(root, isGrouped)
        return fields.fold(obj) { acc, fieldName ->
            val classType = acc.type as JcClassType
            val getter = classType.declaredMethods.single { it.method.generatedGetter(fieldName, false) }
            val v = newVar(getter.returnType)
            val call = JcSpecialCallExpr(getter.methodRef, acc, emptyList())
            genCtx.addInstruction { loc -> JcAssignInst(loc, v, call) }
            v
        }
    }
}
