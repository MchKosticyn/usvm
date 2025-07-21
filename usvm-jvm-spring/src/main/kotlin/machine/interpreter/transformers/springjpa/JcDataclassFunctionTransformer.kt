package machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcBool
import org.jacodb.api.jvm.cfg.JcEqExpr
import org.jacodb.api.jvm.cfg.JcFieldRef
import org.jacodb.api.jvm.cfg.JcIfInst
import org.jacodb.api.jvm.cfg.JcInstRef
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.cfg.JcNewArrayExpr
import org.jacodb.api.jvm.cfg.JcNullConstant
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcStringConstant
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.cfg.JcValue
import org.jacodb.api.jvm.ext.boolean
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.jacodb.impl.types.JcTypedFieldImpl
import org.jacodb.impl.types.substition.JcSubstitutorImpl
import org.usvm.jvm.util.genericTypesFromSignature
import org.usvm.jvm.util.isVoid
import org.usvm.jvm.util.name
import org.usvm.jvm.util.stringType
import org.usvm.jvm.util.toJcClass
import org.usvm.jvm.util.toJcType
import org.usvm.jvm.util.typedField
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import util.database.IdColumnInfo
import util.database.JcTableInfoCollector
import util.database.Relation
import util.database.TableInfo
import util.database.getTableName

// static SomeClass $static_blank_init() { return new SomeClass() }
class JcStaticBlankInitTransformer() : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = method.generatedStaticBlankInit

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val blankObj = generateNewWithInit("blank_obj", method.enclosingClass.toType(), listOf())
        addInstruction { loc -> JcReturnInst(loc, blankObj) }
    }
}

// see in java-stdlib-approximations FirstDataClass's _relationsInit method
class JcRelationsInitTransformer(
    val dataclassTransformer: JcDataclassTransformer,
    val relationChecks: RelationMap<JcField>,
    val cp: JcClasspath,
    val classTable: TableInfo.TableWithIdInfo
) : JcBodyFillerFeature() {

    private val clazz = cp.findClass(classTable.origClassName)
    private val classType = clazz.toType()

    override fun condition(method: JcMethod) = method.generatedRelationsInit

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val thisVal = JcThis(classType)
        val tableManagerType = cp.findType(BASE_TABLE_MANAGER) as JcClassType
        val itableType = cp.findType(ITABLE) as JcClassType

        // Call base init to initialize default field's values
        generateVoidVirtualCall(JAVA_INIT, classType, thisVal, emptyList())

        classTable.orderedRelations().forEachIndexed { ix, rel ->
            val relClass = rel.relatedDataclass(cp)
            val relTblName = getTableName(relClass)

            val tblField = generateManagerAccessWithInit(cp, "fetch_tbl_$ix", relTblName, relClass)

            val fieldValue = when (rel) {
                is Relation.OneToOne, is Relation.ManyToOne -> {
                    val checks = relationChecks.get(clazz, rel.origField)
                    val checkVar = checks.sortedBy(JcField::name).map { check ->
                        generateVirtualCall("oto_${check.name}_$ix", getterName(check), classType, thisVal, emptyList())
                    }.let { putValuesToObjectArray(cp, "oto_${rel.origField.name}_id_check", it) }
                    val tbl = generateVirtualCall(
                        "oto_$ix",
                        TABLE_VALUES_WITH_ID,
                        tableManagerType,
                        tblField,
                        listOf(checkVar)
                    )
                    generateVirtualCall("oto_first_$ix", "first", itableType, tbl, emptyList())
                }

                is Relation.OneToManyByColumn -> {
                    val checkNames = relationChecks.get(relClass, rel.origField).sortedBy(JcField::name)
                        .map { JcStringConstant(it.name, cp.stringType) }
                        .let { putValuesWithSameTypeToArray(cp, "otm_names_$ix", it) }
                    val buildedId = generateVirtualCall("otm_id_$ix", BUILD_ID_NAME, classType, thisVal, emptyList())
                    val values = generateVirtualCall(
                        "otm_$ix",
                        TABLE_VALUES_WITH_FIELDS,
                        tableManagerType,
                        tblField,
                        listOf(buildedId, checkNames)
                    )
                    generateWrapper(rel, values)
                }

                is Relation.RelationByTable -> {
                    val btwTable = generateGlobalNoIdTableAccess(cp, "mtm_table_$ix", rel.joinTable)
                    val buildedId = generateVirtualCall("otm_id_$ix", BUILD_ID_NAME, classType, thisVal, emptyList())

                    val joinTableIxs = rel.joinTable.indexesOf(classTable.origClassName).let {
                        generateIntArray(cp, "join_ixs_$ix", it)
                    }
                    val otherIxs = rel.joinTable.indexesOf(relClass.name).let {
                        generateIntArray(cp, "inverse_join_ixs_$ix", it)
                    }

                    val values = generateVirtualCall(
                        "mtm_$ix",
                        TABLE_VALUES_BY_TABLE,
                        tableManagerType,
                        tblField,
                        listOf(buildedId, btwTable, joinTableIxs, otherIxs)
                    )
                    generateWrapper(rel, values)
                }
            }

            val fieldRef = JcFieldRef(thisVal, rel.origField.typedField)
            addInstruction { loc -> JcAssignInst(loc, fieldRef, fieldValue) }
        }

        addInstruction { loc -> JcReturnInst(loc, null) }
    }

    private fun BlockGenerationContext.generateWrapper(rel: Relation, value: JcValue): JcValue {
        val typeName = when (rel.origField.type.typeName) {
            JAVA_SET -> IMMUTABLE_SET_WRAPPER
            JAVA_LIST -> IMMUTABLE_LIST_WRAPPER
            else -> {
                assert(false)
                IMMUTABLE_LIST_WRAPPER
            }
        }

        return generateNewWithInit("${rel.origField.name}_wrapper", cp.findType(typeName) as JcClassType, listOf(value))
    }
}

// see in java-stdlib-approximations FirstDataClass's _copy method
class JcCopyTransformer(
    val cp: JcClasspath,
    val clazz: JcClassOrInterface,
    val collector: JcTableInfoCollector
) : JcBodyFillerFeature() {

    private val classType = clazz.toType()

    override fun condition(method: JcMethod) = method.generatedCopy

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val thisVar = JcThis(classType)
        val newObj = generateNewWithInit("new_obj", classType, emptyList())

        val fields = collector.collectFields(clazz) { !it.isStatic }
        fields.forEach { field ->
            val fieldTypeName = field.type
            val fieldType = fieldTypeName.toJcType(cp)!! as JcClassType

            val fieldValue =
                generateVirtualCall("get_${field.name}", getterName(field), classType, thisVar, emptyList())

            val fieldCopiedValue = if (fieldTypeName.hasWrapper) {
                generatedWrapperCopy(fieldValue, field, fieldType)
            } else {
                val fieldCopyMethod = fieldType.declaredMethods.singleOrNull { it.method.generatedCopy }
                if (fieldCopyMethod != null) generatedCloneableCopy(fieldValue, field, fieldType)
                else fieldValue
            }

            generateVoidVirtualCall(setterName(field), classType, newObj, listOf(fieldCopiedValue))
        }

        addInstruction { loc -> JcReturnInst(loc, newObj) }
    }

    private fun BlockGenerationContext.generatedWrapperCopy(
        fieldValue: JcLocalVar,
        field: JcField,
        fieldType: JcClassType
    ): JcLocalVar {
        val wrapperType = cp.findType(IWRAPPER) as JcClassType
        val castedValue = generateCast("cast_${field.name}", fieldValue, wrapperType)

        val copyFunction = field.signature!!.genericTypesFromSignature.single()
            .let { cp.findType(it) as JcClassType }
            .declaredMethods
            .single { it.method.generatedCopy && it.method.isStatic }
            .let { generateLambda(cp, "copy_lambda_${field.name}", it.method) }
        val copiedValue = generateVirtualCall(
            "copy_${field.name}",
            "copy",
            wrapperType,
            castedValue,
            listOf(copyFunction)
        )
        return generateCast("backcast_${field.name}", copiedValue, fieldType)
    }

    private fun BlockGenerationContext.generatedCloneableCopy(
        fieldValue: JcLocalVar,
        field: JcField,
        fieldType: JcClassType
    ) = generateVirtualCall("copy_${field.name}", COPY_NAME, fieldType, fieldValue, emptyList())
}

// see in java-stdlib-approximations FirstDataClass's getDTOInfo method
class JcGetDTOTransformer(
    val cp: JcClasspath,
    val clazz: JcClassOrInterface,
    val classTable: TableInfo.TableWithIdInfo,
    val isNeedTrackTable: Boolean
) : JcBodyFillerFeature() {

    private val PACKAGES_FOR_SOFT = listOf("java.lang", "java.time", "java.math")
    private fun isNeedToSoft(field: JcField) = PACKAGES_FOR_SOFT.any { field.type.typeName.startsWith(it) }

    override fun condition(method: JcMethod) = method.generatedGetDTOInfo

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

        val managerType = cp.findType(BASE_TABLE_MANAGER) as JcClassType
        val manager = generateManagerAccess(cp, "manager", classTable.name)
        val cachedDTOInfo = generateVirtualCall("cached_dto", TABLE_GET_DTO_INFO, managerType, manager, emptyList())

        val utilsType = cp.findType(DATABASE_UTILS) as JcClassType
        val checkIsNull = generateStaticCall("check_null", IS_NULL_FUNCTION, utilsType, listOf(cachedDTOInfo))

        addInstruction { loc ->
            val cond = JcEqExpr(cp.boolean, checkIsNull, JcBool(false, cp.boolean))
            val nextInst = JcInstRef(loc.index + 1) // return
            val elseInst = JcInstRef(loc.index + 2) // body of function
            JcIfInst(loc, cond, nextInst, elseInst)
        }
        addInstruction { loc -> JcReturnInst(loc, cachedDTOInfo) } // fast out

        val allFields = classTable.origFieldsInOrder(cp).sortedBy(JcField::name)

        // main part
        val idType = classTable.idColumn.getType(cp).let { generateClassConstant(cp, "id_type", it) }
        val classType = generateClassConstant(cp, "class_type", clazz.toType())
        val tableName = JcStringConstant(classTable.name, cp.stringType)
        val fieldsToValidateNames = allFields
            .filter { it.annotations.any { it.isValidator } }
            .map { JcStringConstant(it.name, cp.stringType) }
            .let { putValuesWithSameTypeToArray(cp, "fields_to_validate_name", it, cp.stringType) }
        val isAutoGeneratedId = JcBool(classTable.isAutoGenerateId(), cp.boolean)
        val isNeedTrack = JcBool(isNeedTrackTable, cp.boolean)

        val staticMethods = clazz.declaredMethods.filter(JcMethod::isStatic)
        val blankInit = generateLambda(cp, "blank_init", staticMethods.single { it.generatedStaticBlankInit })
        val relationsInit = generateLambda(cp, "relations_init", staticMethods.single { it.generatedRelationsInit })
        val buildId = generateLambda(cp, "build_id", staticMethods.single { it.generatedBuildId })

        val specialGetId = staticMethods.singleOrNull { it.generatedSpecialGetId }
            ?.let { generateLambda(cp, "special_get_id", it) }
            ?: JcNullConstant(cp.objectType)
        val specialSetId = staticMethods.singleOrNull { it.generatedSpecialSetId }
            ?.let { generateLambda(cp, "special_set_id", it) }
            ?: JcNullConstant(cp.objectType)

        val copy = generateLambda(cp, "copy", staticMethods.single { it.generatedCopy })

        val fieldsNames = allFields
            .map { JcStringConstant(it.name, cp.stringType) }
            .let { putValuesWithSameTypeToArray(cp, "fields_names", it) }
        val getters = staticMethods.filter { it.generatedGetter }.sortedBy(JcMethod::name)
            .map { generateLambda(cp, "lambda_${it.name}", it) }
            .let { putValuesWithSameTypeToArray(cp, "getters", it) }
        val setters = staticMethods.filter { it.generatedSetter }.sortedBy(JcMethod::name)
            .map { generateLambda(cp, "lambda_${it.name}", it) }
            .let { putValuesWithSameTypeToArray(cp, "setters", it) }

        val fieldsToSoft = allFields.filter(::isNeedToSoft)
        val fieldsToSoftNames = putValuesWithSameTypeToArray(
            cp,
            "fields_to_soft_names",
            fieldsToSoft.map { JcStringConstant(it.name, cp.stringType) }
        )
        val fieldsToSoftTypes = putValuesWithSameTypeToArray(
            cp,
            "fields_to_soft_types",
            fieldsToSoft.map { generateClassConstant(cp, "type_${it.name}", it.type.toJcType(cp)!!) }
        )

        val args = listOf(
            idType,
            classType,
            tableName,
            fieldsToValidateNames,
            isAutoGeneratedId,
            isNeedTrack,
            blankInit,
            relationsInit,
            buildId,
            specialGetId,
            specialSetId,
            copy,
            fieldsNames,
            getters,
            setters,
            fieldsToSoftNames,
            fieldsToSoftTypes
        )
        val newDTOInfo = generateNewWithInit("new_dto_info", cp.findType(DTO_INFO) as JcClassType, args)

        addInstruction { loc -> JcReturnInst(loc, newDTOInfo) }
    }
}

// Object[] $buildId() {
//      val idPart1 = this.idPart1;
//      val idPart2 = this.idPart2;
//      val id = new Object[] { idPart1, idPart2 };
//      return id;
// }
class JcBuildIdTransformer(
    val cp: JcClasspath,
    val classTable: TableInfo.TableWithIdInfo
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = method.generatedBuildId

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val idCol = classTable.idColumn
        val classType = cp.findType(classTable.origClassName) as JcClassType
        val thisVal = JcThis(classType)

        val ids = when (idCol) {
            is IdColumnInfo.SingleId, is IdColumnInfo.ClassId -> {
                idCol.orderedSimpleIds().mapIndexed { ix, col ->
                    val getter = classType.declaredMethods.single { it.method.generatedGetter(col.name, false) }
                    generateVirtualCall("id_part_$ix", getter.name, classType, thisVal, emptyList())
                }
            }

            is IdColumnInfo.EmbeddedId -> {
                val embeddedType = cp.findType(idCol.embeddedClassName) as JcClassType
                val embeddedVar = nextLocalVar("embedded_id", embeddedType)

                val embeddedIdField = classType.fields.single { it.type.typeName.equals(idCol.embeddedClassName) }
                val embeddedFieldRef = JcFieldRef(thisVal, embeddedIdField)
                addInstruction { loc -> JcAssignInst(loc, embeddedVar, embeddedFieldRef) }

                idCol.orderedSimpleIds().mapIndexed { ix, id ->
                    val getterRes = generateVirtualCall(
                        "embedded_call_${ix}",
                        getterName(id.origField),
                        embeddedType,
                        embeddedVar,
                        emptyList()
                    )
                    nextLocalVar("getter_res_${ix}", cp.objectType).also {
                        addInstruction { loc -> JcAssignInst(loc, it, getterRes) }
                    }
                }
            }
        }
        val id = putValuesWithSameTypeToArray(cp, "id", ids)

        addInstruction { loc -> JcReturnInst(loc, id) }
    }
}

// fieldType $getField() { return field; }
class JcGetterTransformer(
    val cp: JcClasspath,
    val field: JcField,
    val name: String
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = name == method.name && method.generatedGetter

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val classType = method.enclosingClass.toType()
        val type = field.type.toJcType(cp)!!

        val vari = nextLocalVar("field", type)
        val fieldRef = JcFieldRef(
            JcThis(classType),
            JcTypedFieldImpl(
                field.enclosingClass.toType(),
                field,
                JcSubstitutorImpl()
            )
        )
        addInstruction { loc -> JcAssignInst(loc, vari, fieldRef) }

        addInstruction { loc -> JcReturnInst(loc, vari) }
    }

}

// void $setField(fieldType field) { this.field = field; }
class JcSetterTransformer(
    val cp: JcClasspath,
    val field: JcField,
    val name: String
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = name == method.name && method.generatedSetter

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val arg = method.parameters.single().toArgument

        val classType = method.enclosingClass.toType()
        val fieldRef = JcFieldRef(
            JcThis(classType),
            JcTypedFieldImpl(
                field.enclosingClass.toType(),
                field,
                JcSubstitutorImpl()
            )
        )
        addInstruction { loc -> JcAssignInst(loc, fieldRef, arg) }

        addInstruction { loc -> JcReturnInst(loc, null) }
    }
}

class JcSpecialGetIdTransformer(
    val cp: JcClasspath,
    val idField: JcField
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = method.generatedSpecialGetId

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val classType = method.enclosingClass.toType()
        val type = idField.type.toJcType(cp)!!

        val vari = nextLocalVar("field", type)
        val fieldRef = JcFieldRef(
            JcThis(classType),
            JcTypedFieldImpl(
                idField.enclosingClass.toType(),
                idField,
                JcSubstitutorImpl()
            )
        )
        addInstruction { loc -> JcAssignInst(loc, vari, fieldRef) }

        addInstruction { loc -> JcReturnInst(loc, vari) }
    }
}

class JcSpecialSetIdTransformer(
    val cp: JcClasspath,
    val idField: JcField
) : JcBodyFillerFeature() {

    override fun condition(method: JcMethod) = method.generatedSpecialSetId

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val arg = method.parameters.single().toArgument

        val classType = method.enclosingClass.toType()
        val fieldRef = JcFieldRef(
            JcThis(classType),
            JcTypedFieldImpl(
                idField.enclosingClass.toType(),
                idField,
                JcSubstitutorImpl()
            )
        )
        addInstruction { loc -> JcAssignInst(loc, fieldRef, arg) }

        addInstruction { loc -> JcReturnInst(loc, null) }
    }
}

// see in java-stdlib-approximations FirstDataClass's _save method
abstract class JcSaveUpdateDeleteTransformer(
    val collector: JcTableInfoCollector,
    val cp: JcClasspath,
    val relationChecks: RelationMap<JcField>,
    val classTable: TableInfo.TableWithIdInfo,
    val clazz: JcClassOrInterface
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

        val complexIdTranslator = if (classTable.idColumn is IdColumnInfo.SingleId) JcNullConstant(cp.objectType)
        else {
            val complexIdClassName = classTable.getComplexIdClassName()!!
            val complexId = cp.findType(complexIdClassName) as JcClassType
            val buildIdsMethod = complexId.declaredMethods.single { it.method.generatedBuildIds }.method
            generateLambda(cp, "complexIdFieldTranslator", buildIdsMethod)
        }

        val crud = generateNewWithInit("crud", crudType, listOf(casted, complexIdTranslator))
        val allowRecUpd = generateVirtualCall("allow_rec_upd", GET_REC_UPD, ctxType, ctxVar, listOf())

        val args = if (modifyCtxFlags) listOf(objVar, allowRecUpd) else listOf(objVar)
        generateVoidVirtualCall(crudMethodName, crudType, crud, args)

        val clazzId = generateVirtualCall("id", BUILD_ID_NAME, classType, objVar, listOf())
        var ix = 0
        val saveUpdDelManagers = classTable.relatedClasses(cp).associate { subClass ->

            val nameSuffix = "${subClass.simpleName}_${ix++}"

            val subType = subClass.toType()
            val subTableField = generateGlobalTableAccess(cp, nameSuffix, getTableName(subClass), subClass)

            val subSaveUpd = subType.declaredMethods.single { it.method.generatedSaveUpdate }
            val subDel = subType.declaredMethods.single { it.method.generatedDelete }
            val subGetId = subType.declaredMethods.single { it.method.generatedBuildId }

            val saveUpd = generateLambda(cp, "save_${nameSuffix}", subSaveUpd.method)
            val del = generateLambda(cp, "del_${nameSuffix}", subDel.method)
            val getId = generateLambda(cp, "id_${nameSuffix}", subGetId.method)

            val manager = generateNewWithInit(
                "manager_${nameSuffix}",
                manyManager,
                listOf(ctxVar, subTableField, saveUpd, del, clazzId, getId)
            )

            subClass.name to manager
        }

        classTable.relations.filter { relFilter(it) }.forEachIndexed { ix, rel ->
            val allowRecUpdate = JcBool(rel.isAllowUpdate, cp.boolean)
            val subClass = rel.relatedDataclass(cp).toType()
            val field = generateVirtualCall("fld_$ix", getterName(rel.origField), classType, objVar, listOf())
            val manager = saveUpdDelManagers[subClass.name]!!
            when (rel) {
                is Relation.OneToOne, is Relation.ManyToOne -> {
                    if (modifyCtxFlags) generateVoidVirtualCall(SET_REC_UPD, ctxType, ctxVar, listOf(allowRecUpdate))
                    generateVoidStaticCall(SAVE_UPDATE_NAME, subClass, listOf(field, ctxVar))
                    val tbl = generateGlobalTableAccess(cp, "tbl_$ix", getTableName(clazz), clazz)
                    val relationFieldsNames = relationChecks.get(clazz, rel.origField).sortedBy(JcField::name)
                        .map { JcStringConstant(it.name, cp.stringType) }
                        .let { putValuesWithSameTypeToArray(cp, "fields_names_$ix", it) }
                    val subId = generateVirtualCall("b${ix}_id", "getId", manyManager, manager, listOf(field))
                    generateVoidVirtualCall(
                        "changeFieldsByIdEnsure",
                        cp.findType(BASE_TABLE_MANAGER) as JcClassType,
                        tbl,
                        listOf(clazzId, relationFieldsNames, subId)
                    )
                }

                is Relation.OneToManyByColumn -> {
                    if (modifyCtxFlags)
                        generateVoidVirtualCall(SET_REC_UPD, manyManager, manager, listOf(allowRecUpdate))
                    val relationFieldsNames =
                        relationChecks.get(subClass.toJcClass()!!, rel.origField).sortedBy(JcField::name)
                            .map { JcStringConstant(it.name, cp.stringType) }
                            .let { putValuesWithSameTypeToArray(cp, "fields_names_$ix", it) }
                    generateVoidVirtualCall(SUD_SAVE_NO_TABLE, manyManager, manager, listOf(field, relationFieldsNames))
                }

                is Relation.RelationByTable -> {
                    val joinTable = rel.joinTable
                    if (modifyCtxFlags) {
                        generateVoidVirtualCall(SET_REC_UPD, manyManager, manager, listOf(allowRecUpd))

                        val parentJoins = joinTable.indexesOf(classTable.origClassName)
                            .let { generateIntArray(cp, "parent_joins_${ix}", it) }
                        generateVoidVirtualCall(SUD_SET_PARENT_JOINS, manyManager, manager, listOf(parentJoins))
                        val childJoins = joinTable.indexesOfOtherClass(classTable.origClassName)
                            .let { generateIntArray(cp, "child_joins_${ix}", it) }
                        generateVoidVirtualCall(SUD_SET_CHILD_JOINS, manyManager, manager, listOf(childJoins))
                    }
                    val join = generateGlobalNoIdTableAccess(cp, "join_$ix", joinTable)
                    generateVoidVirtualCall(SUD_SAVE_WITH_TABLE, manyManager, manager, listOf(field, join))
                }
            }
        }

        addInstruction { loc -> JcReturnInst(loc, null) }
    }
}

// see in java-stdlib-approximations FirstDataClass's _save method
class JcDeleteTransformer(
    collector: JcTableInfoCollector,
    cp: JcClasspath,
    relationChecks: RelationMap<JcField>,
    classTable: TableInfo.TableWithIdInfo,
    clazz: JcClassOrInterface
) : JcSaveUpdateDeleteTransformer(collector, cp, relationChecks, classTable, clazz) {

    override val crudMethodName = "delete"
    override val objMethodName = DELETE_NAME
    override val saveUpdDelMethodNoTableName = SUD_DEL_NO_TABLE
    override val saveUpdDelMethodWithTableName = SUD_DEL_WITH_TABLE
    override val modifyCtxFlags = false

    override fun relFilter(rel: Relation) = rel.isAllowDelete

    override fun condition(method: JcMethod) = method.generatedDelete
}

class JcSaveUpdateTransformer(
    collector: JcTableInfoCollector,
    cp: JcClasspath,
    relationChecks: RelationMap<JcField>,
    classTable: TableInfo.TableWithIdInfo,
    clazz: JcClassOrInterface
) : JcSaveUpdateDeleteTransformer(collector, cp, relationChecks, classTable, clazz) {

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
    val relationChecks: RelationMap<JcField>,
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
                (if (col.isOrig) listOf(col.origField) else relationChecks.get(clazz, col.origField)).forEach {
                    val fieldVar = nextLocalVar("${it.name}_field_${ix}", col.type.toJcType(cp)!!)
                    val field = it.enclosingClass.toType().fields.single { fld -> fld.name == it.name }
                    val fieldRef = JcFieldRef(JcThis(classType), field)
                    addInstruction { loc -> JcAssignInst(loc, fieldVar, fieldRef) }

                    val arrAcess = JcArrayAccess(arr, JcInt(ix, cp.int), cp.objectType)
                    addInstruction { loc -> JcAssignInst(loc, arrAcess, fieldVar) }
                }
            }
        }

        addInstruction { loc -> JcReturnInst(loc, arr) }
    }
}

class JcStaticClassMethod(
    val cp: JcClasspath,
    val newName: String,
    val targetMethod: JcMethod
) : JcBodyFillerFeature() {
    override fun condition(method: JcMethod) =
        method.name == newName && method.isStatic && method.enclosingClass.equals(targetMethod.enclosingClass)

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val obj = method.parameters.first().toArgument
        val args = method.parameters.takeLast(method.parameters.size - 1).map { it.toArgument }
        if (method.isVoid) {
            generateVoidVirtualCall(targetMethod.name, method.enclosingClass.toType(), obj, args)
            addInstruction { loc -> JcReturnInst(loc, null) }
        } else {
            val call = generateVirtualCall("call", targetMethod.name, method.enclosingClass.toType(), obj, args)
            addInstruction { loc -> JcReturnInst(loc, call) }
        }
    }
}
