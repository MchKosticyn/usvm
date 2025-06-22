package machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcArgument
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcCallInst
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcClassConstant
import org.jacodb.api.jvm.cfg.JcEqExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcNewExpr
import org.jacodb.api.jvm.cfg.JcNullConstant
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcSpecialCallExpr
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.cfg.JcVirtualCallExpr
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.cfg.VirtualMethodRefImpl
import org.jacodb.impl.types.JcTypedFieldImpl
import org.jacodb.impl.types.JcTypedMethodImpl
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.usvm.jvm.util.name
import org.usvm.jvm.util.toJcType
import org.usvm.jvm.util.typedField
import org.usvm.jvm.util.typename
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import util.database.Relation
import util.database.TableInfo
import util.database.getTableName

abstract class JcDataclassFunctionTransformer(
    val dataclassTransformer: JcDataclassTransformer,
    val cp: JcClasspath
) : JcBodyFillerFeature() {
    val itableType = cp.findType(ITABLE) as JcClassType
    val setType = cp.findType(SET_WRAPPER) as JcClassType
    val listType = cp.findType(LIST_WRAPPER) as JcClassType
    val mapType = cp.findType(MAP_TABLE) as JcClassType
    val filterType = cp.findType(FILTER_TABLE) as JcClassType
    val managerType = cp.findType(BASE_TABLE_MANAGER) as JcClassType
    val cTyp = cp.findType(JAVA_CLASS) as JcClassType
}

open class JcInitTransformer(
    dataclassTransformer: JcDataclassTransformer,
    cp: JcClasspath,
    val classTable: TableInfo.TableWithIdInfo,
    origInit: JcMethod
) : JcDataclassFunctionTransformer(dataclassTransformer, cp) {

    val clazz = classTable.origClass
    val classType = cp.findType(classTable.origClass.name) as JcClassType
    val parlessInitRef = VirtualMethodRefImpl.of(classType, JcTypedMethodImpl(classType, origInit, JcSubstitutorImpl()))

    override fun condition(method: JcMethod) = method.generatedInit

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val thisVal = JcThis(classType)
        val callInit = JcSpecialCallExpr(parlessInitRef, thisVal, listOf())
        addInstruction { loc -> JcCallInst(loc, callInit) }

        val rowArg = method.parameters.first().toArgument
        classTable.columnsInOrder()
            .filter { !it.origField.checkField }
            .forEachIndexed { ix, col -> generateColAssign(thisVal, rowArg, ix, col) }

        classTable.relations.filterIsInstance<Relation.RelationByTable>().forEach { generateSetAssign(thisVal, it) }

        classTable.orderedRelations().forEachIndexed { ix, rel ->
            val arg = method.parameters[ix + 1].toArgument
            val argVar = nextLocalVar("initArg$ix", arg.type)
            addInstruction { loc -> JcAssignInst(loc, argVar, arg) }
            when (rel) {
                is Relation.OneToOne, is Relation.ManyToOne ->
                    generateSingleObj(thisVal, argVar, rel)
                is Relation.OneToManyByColumn -> generateMultyObj(thisVal, argVar, rel)
                is Relation.RelationByTable -> generateTableObj(thisVal, argVar, rel)
            }
        }

        addInstruction { loc -> JcReturnInst(loc, null) }
    }

    protected fun BlockGenerationContext.generateColAssign(
        thisVal: JcValue,
        rowArg: JcArgument,
        ix: Int,
        col: TableInfo.ColumnInfo
    ) {

        val rowVal = nextLocalVar("${col.name}_row", cp.objectType)
        val access = JcArrayAccess(rowArg, JcInt(ix, cp.int), cp.objectType)
        addInstruction { loc -> JcAssignInst(loc, rowVal, access) }

        val relField = if (col.isOrig) col.origField else dataclassTransformer.relationChecks.get(clazz, col.origField)

        val type = relField.type.toJcType(cp)!!
        val casted = nextLocalVar("${col.name}_row_casted", type)
        val cast = JcCastExpr(type, rowVal)
        addInstruction { loc -> JcAssignInst(loc, casted, cast) }

        val fieldRef = JcFieldRef(
            thisVal,
            JcTypedFieldImpl(relField.enclosingClass.toType(), relField, JcSubstitutorImpl())
        )
        addInstruction { loc -> JcAssignInst(loc, fieldRef, casted) }
    }

    protected fun BlockGenerationContext.generateSingleObj(
        thisVal: JcValue,
        arg: JcLocalVar,
        rel: Relation.RelationByColumn
    ) {

        val fieldName = rel.origField.name

        val method = dataclassTransformer.relationLambdas.get(clazz, rel.origField).single()
        val lambdaVar = generateLambda(cp, "${fieldName}_lambda", method)

        val filterVar = generateNewWithInit("${fieldName}_single", filterType, listOf(arg, lambdaVar))

        val fstVal = nextLocalVar("${fieldName}_fst", cp.objectType)
        val fstMethod = VirtualMethodRefImpl.of(
            filterType, filterType.declaredMethods.single { it.name == "firstEnsure" }
        )
        val fstCall = JcVirtualCallExpr(fstMethod, filterVar, listOf())
        addInstruction { loc -> JcAssignInst(loc, fstVal, fstCall) }

        val relField = rel.origField
        val relType = relField.type.toJcType(cp)!!

        val castedFstVal = nextLocalVar("${fstVal.name}_casted", relType)
        val cast = JcCastExpr(relType, fstVal)
        addInstruction { loc -> JcAssignInst(loc, castedFstVal, cast) }

        val fieldRef = JcFieldRef(thisVal, relField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, castedFstVal) }
    }

    protected fun BlockGenerationContext.generateMultyObj(
        thisVal: JcValue,
        arg: JcLocalVar,
        rel: Relation.OneToManyByColumn
    ) {

        val fieldName = rel.origField.name

        val method = dataclassTransformer.relationLambdas.get(clazz, rel.origField).single()
        val lambdaVar = generateLambda(cp, "${fieldName}_lambda", method)

        val filterVar = generateNewWithInit("${fieldName}_filter", filterType, listOf(arg, lambdaVar))

        val wrapperVar = generateWrapper(rel, filterVar)
        val fieldRef = JcFieldRef(thisVal, rel.origField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, wrapperVar) }
    }

    protected fun BlockGenerationContext.generateTableObj(
        thisVal: JcValue,
        arg: JcLocalVar,
        rel: Relation.RelationByTable
    ) {

        val fieldName = rel.origField.name

        val pred = dataclassTransformer.relationLambdas.get(clazz, rel.origField).single { it.generatedSetFilter }
        val predVar = generateLambda(cp, "${fieldName}_pred", pred)

        val filterVar = generateNewWithInit("${fieldName}_filter_wrapper", filterType, listOf(arg, predVar))

        val wrapperVar = generateWrapper(rel, filterVar)
        val fieldRef = JcFieldRef(thisVal, rel.origField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, wrapperVar) }
    }

    protected fun BlockGenerationContext.generateSetAssign(thisVal: JcValue, rel: Relation.RelationByTable) {

        val fieldName = rel.origField.name

        val btwVar = nextLocalVar("${fieldName}_btw", itableType)
        val btwTable = cp.findType(DATABASES).let { it as JcClassType }.declaredFields
            .single { it.name == rel.joinTable.name }
        val btw = JcFieldRef(null, btwTable)
        addInstruction { loc -> JcAssignInst(loc, btwVar, btw) }

        val pred = dataclassTransformer.relationLambdas.get(clazz, rel.origField).single { it.generatedBtwFilter }
        val predVar = generateLambda(cp, "${fieldName}_lambda", pred)

        val filterVar = generateNewWithInit("${fieldName}_filter", filterType, listOf(btwVar, predVar))

        val sel = dataclassTransformer.relationLambdas.get(clazz, rel.origField).single { it.generatedBtwSelect }
        val selVar = generateLambda(cp, "${fieldName}_sel", sel)

        val typeVar = nextLocalVar("${fieldName}_map_type", cTyp)
        val type = JcClassConstant(sel.returnType.toJcType(cp)!!, cTyp)
        addInstruction { loc -> JcAssignInst(loc, typeVar, type) }

        val mapVar = generateNewWithInit("${fieldName}_map", mapType, listOf(filterVar, selVar, typeVar))

        val setVar = generateNewWithInit("${fieldName}_set", setType, listOf(mapVar))

        val relField = dataclassTransformer.relationSets.get(clazz, rel.origField)
        val fieldRef = JcFieldRef(thisVal, relField.typedField)
        addInstruction { loc -> JcAssignInst(loc, fieldRef, setVar) }
    }

    protected fun BlockGenerationContext.generateWrapper(rel: Relation, tblVar: JcLocalVar): JcLocalVar {
        val relType = rel.origField.type
        val fieldName = rel.origField.name

        val type = when (relType.typeName) {
            JAVA_SET -> setType
            JAVA_LIST -> listType
            // TODO: more collections
            else -> {
                assert(false)
                setType
            }
        }

        return generateNewWithInit("${fieldName}_wrapper", type, listOf(tblVar))
    }
}

// static SomeClass $static_blank_init() { return new SomeClass() }
class JcStaticBlankInitTransformer(
    dataclassTransformer: JcDataclassTransformer,
    cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    origInit: JcMethod
) : JcInitTransformer(dataclassTransformer, cp, classTable, origInit) {

    override fun condition(method: JcMethod) = method.generatedStaticBlankInit

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val blankObj = generateNewWithInit("blank_obj", classType, listOf())
        addInstruction { loc -> JcReturnInst(loc, blankObj) }
    }
}

// new(Object[] row) {
// ITable<SomeClass> tbl1 = new MappedTable(Databases.some_class, SomeClass::new);
// ...
// return new(row, tbl1, tbl2, ...  )
// }
class JcFetchedInitTransformer(
    dataclassTransformer: JcDataclassTransformer,
    cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    origInit: JcMethod
) : JcInitTransformer(dataclassTransformer, cp, classTable, origInit) {

    override fun condition(method: JcMethod) = method.generatedFetchInit

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

        val thisVal = JcThis(classType)
        val callInit = JcSpecialCallExpr(parlessInitRef, thisVal, listOf())
        addInstruction { loc -> JcCallInst(loc, callInit) }

        val rowArg = method.parameters.first().toArgument
        classTable.columnsInOrder()
            .filter { !it.origField.checkField }
            .forEachIndexed { ix, col -> generateColAssign(thisVal, rowArg, ix, col) }

        classTable.relations.filterIsInstance<Relation.RelationByTable>().forEach { generateSetAssign(thisVal, it) }

        classTable.orderedRelations().forEachIndexed { ix, rel ->
            val relClass = rel.relatedDataclass(cp)
            val relTblName = getTableName(relClass)
            val relTable = dataclassTransformer.collector.getTable(relClass)!!

            val thisId = JcArrayAccess(rowArg, JcInt(classTable.idColIndex(), cp.int), cp.objectType)
            val relIdIx = JcInt(relTable.idColIndex(), cp.int)

            val tblField = generateGlobalTableAccess(cp, "fetch_tbl_$ix", relTblName, relClass)

            val fieldValue = when (rel) {
                is Relation.OneToOne, is Relation.ManyToOne -> {
                    val check = dataclassTransformer.relationChecks.get(clazz, rel.origField)
                    val checkVar = generateVirtualCall(
                        "one_to_one_check_$ix",
                        getterName(check),
                        classType,
                        thisVal,
                        emptyList()
                    )

                    val args = listOf(checkVar, relIdIx)
                    val tbl = generateVirtualCall("one_to_one_$ix", "getRowsWithValueAt", managerType, tblField, args)
                    generateVirtualCall("one_to_one_first_$ix", "first", itableType, tbl, emptyList())
                }
                is Relation.OneToManyByColumn -> {
                    val checkIx = JcInt(relTable.indexOfField(rel.origField), cp.int)
                    val args = listOf(thisId, checkIx)
                    val objs = generateVirtualCall("one_to_many_$ix", "getRowsWithValueAt", managerType, tblField, args)

                    generateWrapper(rel, objs)
                }
                is Relation.RelationByTable -> {
                    val btwTable = generateGlobalTableAccess(cp, "many_to_many_table_$ix", rel.joinTable.name, null)

                    val shouldShuffle =
                        JcInt((if (rel.joinTable.joinCol.origField.enclosingClass.name == clazz.name) 0 else 1), cp.int)

                    val args = listOf(thisId, shouldShuffle, btwTable)
                    val objs = generateVirtualCall(
                        "many_to_many_$ix",
                        "getRowsRelatedByTable",
                        managerType,
                        tblField,
                        args
                    )

                    generateWrapper(rel, objs)
                }
            }

            val fieldRef = JcFieldRef(thisVal, rel.origField.typedField)
            addInstruction { loc -> JcAssignInst(loc, fieldRef, fieldValue) }
        }

        addInstruction { loc -> JcReturnInst(loc, null) }
    }
}

class JcStaticFetchedInitTransformer(
    val cp: JcClasspath,
    val classTable: TableInfo.TableWithIdInfo,
    val fetchedInit: JcMethod
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = method.name == STATIC_INIT_NAME

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val classType = cp.findType(classTable.origClass.name) as JcClassType

        val obj = nextLocalVar("stat_obj", classType)
        val newCall = JcNewExpr(classType)
        addInstruction { loc -> JcAssignInst(loc, obj, newCall) }

        val initMethod = classType.declaredMethods.single {
            it.name == JAVA_INIT && it.parameters.size == fetchedInit.parameters.size
        }.methodRef
        val args = listOf(method.parameters.first().toArgument)
        val initCall = JcSpecialCallExpr(initMethod, obj, args)
        addInstruction { loc -> JcCallInst(loc, initCall) }

        addInstruction { loc -> JcReturnInst(loc, obj) }
    }

}

// Integer $getId() { return id; }
class JcGetIdTransformer(
    val cp: JcClasspath,
    val classTable: TableInfo.TableWithIdInfo
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = method.generatedGetId

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

        val clazz = classTable.origClass
        val classType = clazz.typename.toJcType(cp)!!
        val idType = classTable.idColumn.type.toJcType(cp)!!

        val lhv = nextLocalVar("%0", idType)
        val rhv = JcFieldRef(
            JcThis(classType), JcTypedFieldImpl(
                classTable.idColumn.origField.enclosingClass.toType(),
                classTable.idColumn.origField,
                JcSubstitutorImpl()
            )
        )
        addInstruction { loc -> JcAssignInst(loc, lhv, rhv) }

        addInstruction { loc -> JcReturnInst(loc, lhv) }
    }
}

class JcStaticGetIdTransformer(
    val cp: JcClasspath,
    val clazz: JcClassType,
    val getId: JcMethod
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = method.generatedStaticGetId

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val obj = method.parameters.single().toArgument
        val res = generateVirtualCall("res", getId.name, clazz, obj, listOf())
        addInstruction { loc -> JcReturnInst(loc, res) }
    }

}

// fieldType $getField() { return field; }
class JcGetterTransformer(
    val cp: JcClasspath,
    val clazz: JcClassOrInterface,
    val field: JcField,
    val name: String
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = name == method.name && method.generatedGetter

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

        val classType = clazz.typename.toJcType(cp)!!
        val type = field.type.toJcType(cp)!!

        val lhv = nextLocalVar("%0", type)
        val rhv = JcFieldRef(
            JcThis(classType), JcTypedFieldImpl(
                field.enclosingClass.toType(),
                field,
                JcSubstitutorImpl()
            )
        )
        addInstruction { loc -> JcAssignInst(loc, lhv, rhv) }

        addInstruction { loc -> JcReturnInst(loc, lhv) }
    }

}

// someType $identity(someType v) { return v; }
class JcIdentityTransformer(val type: JcType) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = method.generatedIdentity

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val argVal = nextLocalVar("v", type)
        val arg = method.parameters.first().toArgument
        addInstruction { loc -> JcAssignInst(loc, argVal, arg) }
        addInstruction { loc -> JcReturnInst(loc, argVal) }
    }
}

// see in java-stdlib-approximations FirstDataClass's _save method
abstract class JcSaveUpdateDeleteTransformer(
    val dataclassTransformer: JcDataclassTransformer,
    val cp: JcClasspath,
    val classTable: TableInfo.TableWithIdInfo,
    val clazz: JcClassOrInterface,
    val classGetId: JcMethod,
    val classGetters: List<JcMethod>
) : JcBodyFillerFeature() {

    abstract val crudMethodName: String
    abstract val objMethodName: String
    abstract val saveUpdDelMethodNoTableName: String
    abstract val saveUpdDelMethodWithTableName: String
    abstract val modifyCtxFlags: Boolean

    abstract fun relFilter(rel: Relation): Boolean

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

        val classType = clazz.toType()
        val crudType = cp.findType(CRUD_MANAGER) as JcClassType
        val ctxType = cp.findType(SAVE_UPD_DEL_CTX) as JcClassType
        val manyManager = cp.findType(SAVE_UPD_DEL_MANY_MANAGER) as JcClassType

        val objVar = generatedMethodArgumentVar("t", method, 0)
        val ctxVar = generatedMethodArgumentVar("ctx", method, 1)

        val contains = generateVirtualCall("contains", "contains", ctxType, ctxVar, listOf(objVar))

        addInstruction { loc ->
            val cond = JcEqExpr(cp.boolean, contains, JcBool(true, cp.boolean))
            val nextInst = JcInstRef(loc.index + 1) // return
            val elseInst = JcInstRef(loc.index + 2) // body of function
            JcIfInst(loc, cond, nextInst, elseInst)
        }
        addInstruction { loc -> JcReturnInst(loc, null) }

        generateVoidVirtualCall("add", ctxType, ctxVar, listOf(objVar))

        val casted = generateGlobalTableAccess(cp, "tbl", getTableName(clazz), clazz)

        val serializer = clazz.declaredMethods.single { it.generatedStaticSerializerWithSkips }
        val serLmbd = generateLambda(cp, "serilizer", serializer)

        val deserializer = clazz.declaredMethods.single { it.generatedStaticFetchInit }
        val desLmbd = generateLambda(cp, "deserilizer", deserializer)

        val javaClassType = cp.findType(JAVA_CLASS)
        val clTypeV = nextLocalVar("class_type", javaClassType)
        val cl = JcClassConstant(classType, javaClassType)
        addInstruction { loc -> JcAssignInst(loc, clTypeV, cl) }

        val crud = generateNewWithInit("crud", crudType, listOf(casted, serLmbd, desLmbd, clTypeV))
        val allowRecUpd = generateVirtualCall("allow_rec_upd", GET_REC_UPD, ctxType, ctxVar, listOf())

        val args = if (modifyCtxFlags) listOf(objVar, allowRecUpd) else listOf(objVar)
        generateVoidVirtualCall(crudMethodName, crudType, crud, args)

        val clazzId = generateVirtualCall("id", classGetId.name, classType, objVar, listOf())
        val shouldShuffle = JcInt(0, cp.int)
        var ix = 0
        val saveUpdDelManagers = classTable.relatedClasses(cp).associate { subClass ->

            val nameSuffix = "${subClass.simpleName}_${ix++}"

            val subType = subClass.toType()
            val subTableField = generateGlobalTableAccess(cp, nameSuffix, getTableName(subClass), subClass)

            val subSaveUpd = subType.declaredMethods.single { it.method.generatedSaveUpdate }
            val subDel = subType.declaredMethods.single { it.method.generatedDelete }
            val subGetId = subType.declaredMethods.single { it.method.generatedStaticGetId }

            val saveUpd = generateLambda(cp, "save_${nameSuffix}", subSaveUpd.method)
            val del = generateLambda(cp, "del_${nameSuffix}", subDel.method)
            val getId = generateLambda(cp, "id_${nameSuffix}", subGetId.method)

            val manager = generateNewWithInit(
                "manager_${nameSuffix}",
                manyManager,
                listOf(ctxVar, subTableField, saveUpd, del, clazzId, getId, shouldShuffle)
            )

            subClass.name to manager
        }

        classTable.relations.filter { relFilter(it) }.forEachIndexed { ix, rel ->
            val allowRecUpdate = JcBool(rel.isAllowUpdate, cp.boolean)
            val subClass = rel.relatedDataclass(cp).toType()
            val getter = classGetters.single { it.isGeneratedGetter(rel.origField.name) }
            val field = generateVirtualCall("fld_$ix", getter.name, classType, objVar, listOf())
            val manager = saveUpdDelManagers[subClass.name]!!
            when (rel) {
                is Relation.OneToOne, is Relation.ManyToOne -> {
                    if (modifyCtxFlags) generateVoidVirtualCall(SET_REC_UPD, ctxType, ctxVar, listOf(allowRecUpdate))
                    generateVoidStaticCall(SAVE_UPDATE_NAME, subClass, listOf(field, ctxVar))
                    val tbl = generateGlobalTableAccess(cp, "tbl_$ix", getTableName(clazz), clazz)
                    val pos = JcInt(classTable.indexOfField(rel.origField), cp.int)
                    assert(pos.value != -1)
                    val subId = generateVirtualCall("b${ix}_id", "getId", manyManager, manager, listOf(field))
                    generateVoidVirtualCall(
                        "changeSingleFieldByIdEnsure",
                        cp.findType(BASE_TABLE_MANAGER) as JcClassType,
                        tbl,
                        listOf(clazzId, pos, subId)
                    )
                }

                is Relation.OneToManyByColumn -> {
                    if (modifyCtxFlags)
                        generateVoidVirtualCall(SET_REC_UPD, manyManager, manager, listOf(allowRecUpdate))
                    val pos = JcInt(
                        dataclassTransformer.collector.getTable(subClass.jcClass)!!.indexOfField(rel.origField),
                        cp.int
                    )
                    assert(pos.value != -1)
                    generateVoidVirtualCall(SUD_SAVE_NO_TABLE, manyManager, manager, listOf(field, pos))
                }

                is Relation.RelationByTable -> {
                    val joinTable = rel.joinTable.toTable()
                    if (modifyCtxFlags) {
                        val shuffle =
                            if (joinTable.columnsInOrder().first().origField.enclosingClass.name == clazz.name)
                                JcInt(0, cp.int)
                            else JcInt(1, cp.int)
                        generateVoidVirtualCall(SET_REC_UPD, manyManager, manager, listOf(allowRecUpd))
                        generateVoidVirtualCall(SUD_SET_SHOULD_SHUFFLE, manyManager, manager, listOf(shuffle))
                    }
                    val join = generateGlobalTableAccess(cp, "join_$ix", joinTable.name, null)
                    generateVoidVirtualCall(SUD_SAVE_WITH_TABLE, manyManager, manager, listOf(field, join))
                }
            }
        }

        addInstruction { loc -> JcReturnInst(loc, null) }
    }
}

// see in java-stdlib-approximations FirstDataClass's _save method
class JcDeleteTransformer(
    dataclassTransformer: JcDataclassTransformer,
    cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    clazz: JcClassOrInterface,
    classGetId: JcMethod,
    classGetters: List<JcMethod>
) : JcSaveUpdateDeleteTransformer(dataclassTransformer, cp, classTable, clazz, classGetId, classGetters) {

    override val crudMethodName = "delete"
    override val objMethodName = DELETE_NAME
    override val saveUpdDelMethodNoTableName = SUD_DEL_NO_TABLE
    override val saveUpdDelMethodWithTableName = SUD_DEL_WITH_TABLE
    override val modifyCtxFlags = false

    override fun relFilter(rel: Relation) = rel.isAllowDelete

    override fun condition(method: JcMethod) = method.generatedDelete
}

class JcSaveUpdateTransformer(
    dataclassTransformer: JcDataclassTransformer,
    cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    clazz: JcClassOrInterface,
    classGetId: JcMethod,
    classGetters: List<JcMethod>
) : JcSaveUpdateDeleteTransformer(dataclassTransformer, cp, classTable, clazz, classGetId, classGetters) {

    override val crudMethodName = "save"
    override val objMethodName = SAVE_UPDATE_NAME
    override val saveUpdDelMethodNoTableName = SUD_SAVE_NO_TABLE
    override val saveUpdDelMethodWithTableName = SUD_SAVE_WITH_TABLE
    override val modifyCtxFlags = true

    override fun relFilter(rel: Relation) = rel.isAllowSave || rel.isAllowUpdate

    override fun condition(method: JcMethod) = method.generatedSaveUpdate
}


// Object[] $serialize() {
//      val row = new Object[4];
//      row[0] = id;
//      ...
//      return row
// }
class JcSerializerTransformer(
    val cp: JcClasspath,
    val dataclassTransformer: JcDataclassTransformer,
    val classTable: TableInfo.TableWithIdInfo,
    val skipGeneratedFields: Boolean
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) =
        !skipGeneratedFields && method.generatedSerializer
                || skipGeneratedFields && method.generatedSerializerWithSkips

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val clazz = method.enclosingClass
        val classType = clazz.toType()
        val columns = classTable.columnsInOrder()

        val arrType = cp.arrayTypeOf(cp.objectType, true, listOf())
        val arr = nextLocalVar("row", arrType)
        val newArr = JcNewArrayExpr(arrType, listOf(JcInt(columns.size, cp.int)))
        addInstruction { loc -> JcAssignInst(loc, arr, newArr) }

        columns.forEachIndexed { ix, col ->

            if (!(skipGeneratedFields && !col.isOrig)) {
                val fieldVar = nextLocalVar("${col.name}_field", col.type.toJcType(cp)!!)
                val field =
                    if (col.isOrig) col.origField else dataclassTransformer.relationChecks.get(clazz, col.origField)
                val fieldRef = JcFieldRef(
                    JcThis(classType), JcTypedFieldImpl(
                        field.enclosingClass.toType(),
                        field,
                        JcSubstitutorImpl()
                    )
                )
                addInstruction { loc -> JcAssignInst(loc, fieldVar, fieldRef) }

                val arrAcess = JcArrayAccess(arr, JcInt(ix, cp.int), cp.objectType)
                addInstruction { loc -> JcAssignInst(loc, arrAcess, fieldVar) }
            }
        }

        addInstruction { loc -> JcReturnInst(loc, arr) }
    }
}

class JcStaticSerializerTransformer(
    val cp: JcClasspath,
    val classTable: TableInfo.TableWithIdInfo,
    val serializer: JcMethod,
    val skipGenerated: Boolean
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) =
        !skipGenerated && method.generatedStaticSerializer
                || skipGenerated && method.generatedStaticSerializerWithSkips

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val classType = cp.findType(classTable.origClass.name) as JcClassType

        val arrType = cp.arrayTypeOf(cp.objectType, false, listOf())
        val arrVar = nextLocalVar("ser_obj", arrType)

        val serMethod = classType.declaredMethods.single {
            it.name == serializer.name && it.parameters.size == serializer.parameters.size
        }
        val serMethodRef = VirtualMethodRefImpl.of(classType, serMethod)
        val inst = method.parameters.single().toArgument
        val serCall = JcVirtualCallExpr(serMethodRef, inst, listOf())

        addInstruction { loc -> JcAssignInst(loc, arrVar, serCall) }

        addInstruction { loc -> JcReturnInst(loc, arrVar) }
    }
}
