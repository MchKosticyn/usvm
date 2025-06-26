package machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcParameter
import org.jacodb.api.jvm.JcPrimitiveType
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypeVariable
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.JcTypedMethodParameter
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.BsmHandleTag
import org.jacodb.api.jvm.cfg.BsmMethodTypeArg
import org.jacodb.api.jvm.cfg.JcArgument
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcClassConstant
import org.jacodb.api.jvm.cfg.JcConditionExpr
import org.jacodb.api.jvm.cfg.JcEqExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcGotoInst
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLambdaExpr
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcNewExpr
import org.jacodb.api.jvm.cfg.JcRawArgument
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcStaticCallExpr
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.isAssignable
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.cfg.TypedMethodRefImpl
import org.jacodb.impl.cfg.TypedStaticMethodRefImpl
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.jacodb.impl.types.AnnotationInfo
import org.usvm.api.decoder.DummyField
import org.usvm.jvm.util.genericTypesFromSignature
import org.usvm.jvm.util.getTypename
import org.usvm.jvm.util.isVoid
import org.usvm.jvm.util.toJcClass
import org.usvm.jvm.util.toJcType
import org.usvm.jvm.util.typeName
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import util.database.contains
import util.database.nameEquals

// region GeneratedFunctions

const val STATIC_INIT_NAME = "\$static_init"
const val STATIC_BLANK_INIT_NAME = "\$static_blank_init"
const val SERIALIZER_NAME = "\$serializer"
const val SERIALIZER_WITH_SKIPS_NAME = "\$serializer_with_skips"
const val GET_ID_NAME = "\$special_get_id"
const val STATIC_GET_ID_NAME = "\$static_get_id"
const val STATIC_SERIALIZER_NAME = "\$stat_serializer"
const val STATIC_SERIALIZER_WITH_SKIPS_NAME = "\$stat_serializer_with_skips"
const val IDENTITY_NAME = "\$identity"
const val SAVE_UPDATE_NAME = "\$save_update"
const val DELETE_NAME = "\$delete"

// endregion

// region JavaNames

const val JAVA_INIT = "<init>"

const val JAVA_VOID = "void"
const val JAVA_OBJ_ARR = "java.lang.Object[]"
const val JAVA_CLASS = "java.lang.Class"

const val JAVA_INTEGER = "java.lang.Integer"
const val JAVA_BOOL = "java.lang.Boolean"
const val JAVA_BYTE = "java.lang.Byte"
const val JAVA_CHAR = "java.lang.Character"
const val JAVA_LONG = "java.lang.Long"
const val JAVA_SHORT = "java.lang.Short"
const val JAVA_FLOAT = "java.lang.Float"
const val JAVA_DOUBLE = "java.lang.Double"

const val JAVA_SET = "java.util.Set"
const val JAVA_LIST = "java.util.List"
const val JAVA_MAP = "java.util.Map"
const val JAVA_STRING = "java.lang.String"
const val JAVA_BIG_INT = "java.math.BigInteger"
const val JAVA_BIG_DECIMAL = "java.math.BigDecimal"

// endregion

// region GlobalEntities

const val DATABASES = "stub.spring.SpringDatabases"
const val CRUD_MANAGER = "generated.org.springframework.boot.databases.saveupddel.CrudManager"
const val SAVE_UPD_DEL_CTX = "generated.org.springframework.boot.databases.saveupddel.SaveUpdDelCtx"
const val GET_REC_UPD = "getAllowRecursiveUpdate"
const val SET_REC_UPD = "setAllowRecursiveUpdate"
const val SAVE_UPD_DEL_MANY_MANAGER = "generated.org.springframework.boot.databases.saveupddel.SaveUpdDelManyManager"
const val SUD_SET_SHOULD_SHUFFLE = "setShouldShuffle"
const val SUD_SAVE_NO_TABLE = "saveUpdWithoutRelationTable"
const val SUD_SAVE_WITH_TABLE = "saveUpd"
const val SUD_DEL_NO_TABLE = "delWithoutRelationTable"
const val SUD_DEL_WITH_TABLE = "delete"

// endregion

// region Tables

const val ITABLE = "generated.org.springframework.boot.databases.ITable"
const val BASE_TABLE_MANAGER = "generated.org.springframework.boot.databases.basetables.BaseTableManager"
const val NO_ID_TABLE_MANAGER = "generated.org.springframework.boot.databases.basetables.NoIdTableManager"
const val MAP_TABLE = "generated.org.springframework.boot.databases.MappedTable"
const val FILTER_TABLE = "generated.org.springframework.boot.databases.FiltredTable"
const val HAVING_TABLE = "generated.org.springframework.boot.databases.HavingTable"
const val SORTED_TABLE = "generated.org.springframework.boot.databases.SortedTable"
const val GROUP_BY_TABLE = "generated.org.springframework.boot.databases.GroupByTable"
const val JOIN_TABLE = "generated.org.springframework.boot.databases.JoinedTable"
const val DISTINCT_TABLE = "generated.org.springframework.boot.databases.DistinctTable"
const val FLAT_TABLE = "generated.org.springframework.boot.databases.FlatTable"
const val SINGLETON_TABLE = "generated.org.springframework.boot.databases.SingletonTable"
const val AGGREGATORS = "generated.org.springframework.boot.databases.utils.Aggregators"
const val DATABASE_UTILS = "generated.org.springframework.boot.databases.utils.DatabaseSupportFunctions"

// endregion

// region Wrappers

const val IWRAPPER = "generated.org.springframework.boot.databases.wrappers.IWrapper"
const val PAGE_WRAPPER = "org.springframework.data.domain.Page"
const val PAGE_IMPL_WRAPPER = "org.springframework.data.domain.PageImpl"
const val SET_WRAPPER = "generated.org.springframework.boot.databases.wrappers.SetWrapper"
const val LIST_WRAPPER = "generated.org.springframework.boot.databases.wrappers.ListWrapper"

// endregion

// region Annotations

const val APPROX_NAME = "org.jacodb.approximation.annotation.Approximate"

// endregion

// region DummyAnnotation

const val CHECK_FIELD_ANNOT = "\$check_field_annot"
const val INIT_ANNOT = "\$generated_init_annot"
const val INIT_FETCH_ANNOT = "\$generated_fetched_annot"
const val STATIC_INIT_FETCH_ANNOT = "\$generated_static_fetched_annot"
const val STATIC_BLANK_INIT_ANNOT = "\$generated_static_blank_init_annot"
const val GET_ID_ANNOT = "\$generated_get_id"
const val STATIC_GET_ID_ANNOT = "\$generated_static_get_id"
const val DATACLASS_GETTER = "\$generated_dataclass_getter"
const val ONE_TO_MANY_FILTER_ANNOT = "\$generated_onetomany_filter"
const val MANY_TO_ONE_FILTER_ANNOT = "\$generated_manytoone_filter"
const val FILTER_BTW_ANNOT = "\$generated_btw_filter"
const val SELECT_BTW_ANNOT = "\$generated_btw_selector"
const val FILTER_SET_ANNOT = "\$generated_set_filter"
const val SERIALIZER_ANNOT = "\$generated_serializer"
const val SERIALIZER_WITH_SKIPS_ANNOT = "\$generated_serializer_with_skips"
const val STATIC_SERIALIZER_ANNOT = "\$generated_stat_serializer"
const val STATIC_SERIALIZER_WITH_SKIPS_ANNOT = "\$generated_stat_serializer_with_skips"
const val IDENTITY_ANNOT = "\$generated_ident"
const val SAVE_UPDATE_ANNOT = "\$save_update_annot"
const val DELETE_ANNOT = "\$delete_annotw"
const val REPOSITORY_LAMBDA = "\$query_lambda"

// endregion

// region LambdaNames

const val LAMBDA_METAFACTORY = "java.lang.invoke.LambdaMetafactory"
const val METAFACTORY = "metafactory"
const val PREDICATE = "java.util.function.Predicate"
const val FUNCTION = "java.util.function.Function"
const val FUNCTION2 = "java.util.function.BiFunction"
const val FUNCTION3 = "org.assertj.core.util.TriFunction"
const val SUPPLIER = "java.util.function.Supplier"
const val CONSUMER = "java.util.function.Consumer"
const val CONSUMER2 = "java.util.function.BiConsumer"

// endregion

// region AnnotationCheck

val JcMethod.generatedOneToManyFilter: Boolean get() = contains(this.annotations, ONE_TO_MANY_FILTER_ANNOT)
val JcMethod.generatedManyToOneFilter: Boolean get() = contains(this.annotations, MANY_TO_ONE_FILTER_ANNOT)
val JcMethod.generatedBtwFilter: Boolean get() = contains(this.annotations, FILTER_BTW_ANNOT)
val JcMethod.generatedBtwSelect: Boolean get() = contains(this.annotations, SELECT_BTW_ANNOT)
val JcMethod.generatedSetFilter: Boolean get() = contains(this.annotations, FILTER_SET_ANNOT)
val JcMethod.generatedGetId: Boolean get() = contains(annotations, GET_ID_ANNOT)
val JcMethod.generatedStaticGetId: Boolean get() = contains(annotations, STATIC_GET_ID_ANNOT)
val JcMethod.generatedGetter: Boolean get() = contains(annotations, DATACLASS_GETTER)
val JcMethod.generatedIdentity: Boolean get() = contains(annotations, IDENTITY_ANNOT)
val JcMethod.generatedSerializer: Boolean get() = contains(annotations, SERIALIZER_ANNOT)
val JcMethod.generatedSerializerWithSkips: Boolean get() = contains(annotations, SERIALIZER_WITH_SKIPS_ANNOT)
val JcMethod.generatedStaticSerializer: Boolean get() = contains(annotations, STATIC_SERIALIZER_ANNOT)
val JcMethod.generatedStaticSerializerWithSkips: Boolean
    get() = contains(
        annotations,
        STATIC_SERIALIZER_WITH_SKIPS_ANNOT
    )
val JcMethod.generatedInit: Boolean get() = contains(this.annotations, INIT_ANNOT)
val JcMethod.generatedFetchInit: Boolean get() = contains(this.annotations, INIT_FETCH_ANNOT)
val JcMethod.generatedStaticFetchInit: Boolean get() = contains(this.annotations, STATIC_INIT_FETCH_ANNOT)
val JcMethod.generatedStaticBlankInit: Boolean get() = contains(this.annotations, STATIC_BLANK_INIT_ANNOT)
val JcMethod.generatedSaveUpdate: Boolean get() = contains(this.annotations, SAVE_UPDATE_ANNOT)
val JcMethod.generatedDelete: Boolean get() = contains(this.annotations, DELETE_ANNOT)
val JcMethod.repositoryLambda: Boolean get() = contains(annotations, REPOSITORY_LAMBDA)

val JcField.checkField: Boolean get() = contains(annotations, CHECK_FIELD_ANNOT)

// endregion

fun JcMethod.isGeneratedGetter(fieldName: String): Boolean {
    return contains(annotations, DATACLASS_GETTER) && contains(annotations, fieldName)
}

private val repositoryNames = setOf("org.springframework.data.repository.Repository", "org.springframework.data.jpa.repository.JpaRepository")

val JcClassOrInterface.isDataClass: Boolean get() = contains(annotations, "Entity")
val JcClassOrInterface.isJpaRepository: Boolean
    get() =
        interfaces.any { repositoryNames.contains(it.name) }

val JcTypedMethod.methodRef: TypedMethodRefImpl
    get() = TypedMethodRefImpl(
        enclosingType as JcClassType,
        name,
        method.parameters.map { it.type },
        method.returnType
    )

val JcTypedMethod.staticMethodRef: TypedStaticMethodRefImpl
    get() = TypedStaticMethodRefImpl(
        enclosingType as JcClassType,
        name,
        method.parameters.map { it.type },
        method.returnType
    )

val JcMethod.query: String?
    get() =
        annotations.find { nameEquals(it, "Query") }?.values?.get("value") as String?

val JcParameter.parameterName: String
    get() =
        annotations.find { nameEquals(it, "Param") }?.values?.get("value") as String?
            ?: name!!

val JcParameter.toArgument: JcArgument
    get() = JcArgument(index, name!!, type.toJcType(method.enclosingClass.classpath)!!)

val JcParameter.toRawArgument: JcRawArgument
    get() = JcRawArgument(index, name!!, type)

val dummyAnnot = AnnotationInfo(DummyField::class.java.name, true, listOf(), null, null)

// returns value type of Map, element type of Collection and just type otherwise
val JcType.getNextType: JcType
    get() {
        return toJcClass()?.signature?.genericTypesFromSignature?.get(
            if (typeName == JAVA_MAP) 1 else 0
        )?.let { this.classpath.findType(it) }
            ?: this
    }

fun findMethod(
    clazz: JcClassType,
    name: String,
    args: List<JcValue>,
    filter: (JcTypedMethod) -> Boolean = { true }
): JcTypedMethod {

    fun checkGeneric(p: JcTypedMethodParameter, a: JcValue) =
        (p.type is JcTypeVariable && a.type !is JcPrimitiveType)
                || (a.type is JcTypeVariable && p.type !is JcPrimitiveType)

    fun checkAssignable(p: JcTypedMethodParameter, a: JcValue) =
        p.type.toJcClass()?.toType()?.let { ptype -> a.type.isAssignable(ptype) } // assignable ref types
            ?: (a.type == p.type) // primitive types

    return clazz.declaredMethods.single {
        it.name == name && filter(it) && it.parameters.size == args.size
                && it.parameters.zip(args).all { (p, a) ->
            checkGeneric(p, a) || checkAssignable(p, a)
        }
    }
}

// region BlockGenerators

fun BlockGenerationContext.generateIsEqual(
    cp: JcClasspath,
    name: String,
    left: JcLocalVar,
    right: JcLocalVar
): JcLocalVar {
    val compared = generateStaticCall(
        "compared_${name}",
        "comparer",
        cp.findType(DATABASE_UTILS) as JcClassType,
        listOf(left, right)
    )
    val downcasted = toInt(cp, compared)

    val cond = JcEqExpr(cp.boolean, downcasted, JcInt(0, cp.int))
    val ifRes = compare(cp, cond, name)

    return toBoolean(cp, ifRes)
}


fun BlockGenerationContext.compare(cp: JcClasspath, cond: JcConditionExpr, name: String): JcLocalVar {
    val endOfIf: JcInstRef
    addInstruction { loc ->
        val nextInst = JcInstRef(loc.index + 1)
        val elseBranch = JcInstRef(loc.index + 3)
        endOfIf = JcInstRef(loc.index + 5)
        JcIfInst(loc, cond, nextInst, elseBranch)
    }

    val ifResVal = nextLocalVar("if_$name", cp.boolean)
    addInstruction { loc -> JcAssignInst(loc, ifResVal, JcBool(true, cp.boolean)) }
    addInstruction { loc -> JcGotoInst(loc, endOfIf) }
    addInstruction { loc -> JcAssignInst(loc, ifResVal, JcBool(false, cp.boolean)) }
    addInstruction { loc -> JcGotoInst(loc, endOfIf) }

    return ifResVal
}

fun BlockGenerationContext.toBoolean(cp: JcClasspath, value: JcLocalVar): JcLocalVar {
    val boolType = cp.findType(JAVA_BOOL) as JcClassType
    return generateStaticCall("to_bool_${value.name}", "valueOf", boolType, listOf(value))
}

fun BlockGenerationContext.toInt(cp: JcClasspath, value: JcLocalVar): JcLocalVar {
    val integerType = cp.findType(JAVA_INTEGER) as JcClassType
    return generateVirtualCall("to_int_${value.name}", "intValue", integerType, value, listOf())
}

fun BlockGenerationContext.toJavaClass(cp: JcClasspath, name: String, type: JcType): JcLocalVar {
    val classType = cp.findType(JAVA_CLASS) as JcClassType
    val typeVar = nextLocalVar("${name}_type_const", classType)
    val typ = JcClassConstant(type, classType)
    addInstruction { loc -> JcAssignInst(loc, typeVar, typ) }
    return typeVar
}

fun BlockGenerationContext.generatedMethodArgumentVar(name: String, method: JcMethod, pos: Int): JcLocalVar {
    val arg = method.parameters[pos].toArgument
    val vari = nextLocalVar(name, arg.type)
    addInstruction { loc -> JcAssignInst(loc, vari, arg) }
    return vari
}

fun BlockGenerationContext.generateNew(name: String, type: JcType): JcLocalVar {
    val vari = nextLocalVar(name, type)
    val newExpr = JcNewExpr(type)
    addInstruction { loc -> JcAssignInst(loc, vari, newExpr) }
    return vari
}

fun BlockGenerationContext.generateObjectArray(cp: JcClasspath, name: String, size: Int): JcLocalVar {
    val objArrayType = cp.arrayTypeOf(cp.objectType, true, listOf())
    val vari = nextLocalVar(name, objArrayType)
    val arr = JcNewArrayExpr(objArrayType, listOf(JcInt(size, cp.int)))
    addInstruction { loc -> JcAssignInst(loc, vari, arr) }
    return vari;
}

fun BlockGenerationContext.putArgumentsToArray(cp: JcClasspath, name: String, method: JcMethod): JcLocalVar {
    val arr = generateObjectArray(cp, "args#$name", method.parameters.size)
    method.parameters.forEachIndexed { ix, p ->
        val access = JcArrayAccess(arr, JcInt(ix, cp.int), cp.objectType)
        addInstruction { loc -> JcAssignInst(loc, access, p.toArgument) }
    }
    return arr
}

fun BlockGenerationContext.putValuesWithSameTypeToArray(
    cp: JcClasspath,
    name: String,
    values : List<JcLocalVar>
): JcLocalVar {
    if (values.isEmpty()) return generateObjectArray(cp, name, 0)

    val valueType = values.first().type
    val arrType = cp.arrayTypeOf(valueType, true, emptyList())
    val vari = nextLocalVar("arr#$name", arrType)
    val arr = JcNewArrayExpr(arrType, listOf(JcInt(values.size, cp.int)))
    addInstruction { loc -> JcAssignInst(loc, vari, arr) }

    values.forEachIndexed { ix, v ->
        val arrAcc = JcArrayAccess(vari, JcInt(ix, cp.int), valueType)
        addInstruction { loc -> JcAssignInst(loc, arrAcc, v) }
    }

    return vari
}

fun BlockGenerationContext.generateNewWithInit(name: String, type: JcClassType, args: List<JcValue>): JcLocalVar {
    val vari = generateNew(name, type)
    val init = type.declaredMethods.filter {
        it.name == JAVA_INIT && it.parameters.size == args.size
    }.let {
        if (it.size == 1) it.single()
        else
        // TODO: think to do with Owner.isAssignable(T)
            it.single { m ->
                m.parameters.zip(args).all { (p, a) ->
                    a.type.isAssignable(p.type.toJcClass()!!.toType())
                }
            }
    }

    val call = JcSpecialCallExpr(init.methodRef, vari, args)
    addInstruction { loc -> JcCallInst(loc, call) }
    return vari
}

fun BlockGenerationContext.generateStaticCall(
    name: String,
    methodName: String,
    clazz: JcClassType,
    args: List<JcValue>
): JcLocalVar {
    val method = findMethod(clazz, methodName, args) { it.isStatic }
    val res = nextLocalVar(name, method.returnType)
    val call = JcStaticCallExpr(method.methodRef, args)
    addInstruction { loc -> JcAssignInst(loc, res, call) }
    return res
}

fun BlockGenerationContext.generateVirtualCall(
    name: String,
    methodName: String,
    clazz: JcClassType,
    inst: JcValue,
    args: List<JcValue>
): JcLocalVar {
    val method = findMethod(clazz, methodName, args) { !it.isStatic }
    val ref = VirtualMethodRefImpl.of(clazz, method)
    val res = nextLocalVar(name, method.returnType)
    val call = JcVirtualCallExpr(ref, inst, args)
    if (method.returnType.typeName != JAVA_VOID) addInstruction { loc -> JcAssignInst(loc, res, call) }
    return res
}

fun BlockGenerationContext.generateVoidStaticCall(
    methodName: String,
    clazz: JcClassType,
    args: List<JcValue>
) {
    val method = findMethod(clazz, methodName, args) { it.isStatic && it.returnType.typeName == JAVA_VOID }
    val ref = method.staticMethodRef
    val call = JcStaticCallExpr(ref, args)
    addInstruction { loc -> JcCallInst(loc, call) }
}

fun BlockGenerationContext.generateVoidVirtualCall(
    methodName: String,
    clazz: JcClassType,
    inst: JcValue,
    args: List<JcValue>
) {

    val method = findMethod(clazz, methodName, args) { !it.isStatic && it.returnType.typeName == JAVA_VOID }
    val ref = VirtualMethodRefImpl.of(clazz, method)
    val call = JcVirtualCallExpr(ref, inst, args)
    addInstruction { loc -> JcCallInst(loc, call) }
}

fun BlockGenerationContext.generateGlobalTableAccess(
    cp: JcClasspath,
    name: String,
    tableName: String,
    clazz: JcClassOrInterface?
): JcLocalVar {
    val isNoIdTable = clazz == null

    val tblV = nextLocalVar(name, cp.findType(ITABLE))
    val tblField = (cp.findType(DATABASES) as JcClassType).fields.single { it.name == tableName }
    val tblRef = JcFieldRef(null, tblField)
    addInstruction { loc -> JcAssignInst(loc, tblV, tblRef) }

    val baseType = cp.findType(if (isNoIdTable) NO_ID_TABLE_MANAGER else BASE_TABLE_MANAGER)
    val casted = nextLocalVar("casted_$name", baseType)
    val cast = JcCastExpr(baseType, tblV)
    addInstruction { loc -> JcAssignInst(loc, casted, cast) }

    if (!isNoIdTable) {
        putSetDeserializerCall(cp, name, casted, clazz)
        putSetFunctionsGeneratedIdTableCall(cp, name, casted, clazz)
    }

    return casted
}

private fun BlockGenerationContext.putSetDeserializerCall(
    cp: JcClasspath,
    name: String,
    table: JcLocalVar,
    clazz: JcClassOrInterface
) {
    val deserializerMethod = clazz.declaredMethods.single { it.generatedStaticFetchInit }
    val deserializer = generateLambda(cp, "deserializer_${name}", deserializerMethod)

    val manager = cp.findType(BASE_TABLE_MANAGER) as JcClassType
    generateVoidVirtualCall("setDeserializerTrackTable", manager, table, listOf(deserializer))
}

private fun BlockGenerationContext.putSetFunctionsGeneratedIdTableCall(
    cp: JcClasspath,
    name: String,
    table: JcLocalVar,
    clazz: JcClassOrInterface
) {
    val blankInitMethod = clazz.declaredMethods.single { it.generatedStaticBlankInit }
    val blankInit = generateLambda(cp, "blak_init_${name}", blankInitMethod)

    val getIdMethod = clazz.declaredMethods.single { it.generatedStaticGetId }
    val getId = generateLambda(cp, "get_id_${name}", getIdMethod)

    val manager = cp.findType(BASE_TABLE_MANAGER) as JcClassType
    generateVoidVirtualCall("setFunctionsGeneratedIdTable", manager, table, listOf(blankInit, getId))
}

fun BlockGenerationContext.generateLambda(
    cp: JcClasspath,
    name: String,
    method: JcMethod
): JcLocalVar {
    val dynMethodRet = if (method.isVoid) JAVA_VOID.typeName else cp.objectType.getTypename()

    val (callSiteRetTypeName, callSiteName) = when ((method.parameters.size) to (method.isVoid)) {
        (0 to false) -> SUPPLIER to "get"
        (1 to true) -> CONSUMER to "accept"
        (2 to true) -> CONSUMER2 to "accept"
        (1 to false) -> FUNCTION to "apply"
        (2 to false) -> FUNCTION2 to "apply"
        (3 to false) -> FUNCTION3 to "apply"
        else -> error("unknown lambda type")
    }

    val callSiteRetType = cp.findType(callSiteRetTypeName)

    val lambdaVar = nextLocalVar(name, callSiteRetType)
    val lambda = getLambda(cp, method, callSiteName, callSiteRetType, dynMethodRet)
    addInstruction { loc -> JcAssignInst(loc, lambdaVar, lambda) }

    return lambdaVar
}

fun getLambda(
    cp: JcClasspath,
    method: JcMethod,
    callSiteName: String,
    callSiteRetType: JcType,
    dynMethodRet: TypeName
): JcLambdaExpr {

    val bsm = cp.findType(LAMBDA_METAFACTORY).let { it as JcClassType }
        .declaredMethods.single { it.name == METAFACTORY }
        .methodRef

    val classType = cp.findType(method.enclosingClass.name) as JcClassType
    val argTypes = method.parameters.map { it.type }
    val actualMethod =
        if (method.isStatic) TypedStaticMethodRefImpl(classType, method.name, argTypes, method.returnType)
        else TypedMethodRefImpl(classType, method.name, argTypes, method.returnType)
    val interfaceMethodType = BsmMethodTypeArg(argTypes, method.returnType)
    val dynamicMethodType = BsmMethodTypeArg(argTypes.map { cp.objectType.getTypename() }, dynMethodRet)

    val callSiteArgTypes = if (method.isStatic) listOf() else listOf(classType as JcType)
    val callSiteArgs = if (method.isStatic) listOf() else listOf(JcThis(classType))

    return JcLambdaExpr(
        bsm,
        actualMethod,
        interfaceMethodType,
        dynamicMethodType,
        callSiteName,
        callSiteArgTypes,
        callSiteRetType,
        callSiteArgs,
        if (method.isStatic) BsmHandleTag.MethodHandle.INVOKE_STATIC else BsmHandleTag.MethodHandle.INVOKE_VIRTUAL
    )
}

// endregion
