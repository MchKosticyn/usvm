package machine.interpreter.transformers.springjpa

import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcArrayAccess
import org.jacodb.api.jvm.cfg.JcAssignInst
import org.jacodb.api.jvm.cfg.JcCastExpr
import org.jacodb.api.jvm.cfg.JcInt
import org.jacodb.api.jvm.cfg.JcReturnInst
import org.jacodb.api.jvm.cfg.JcThis
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.findType
import org.jacodb.api.jvm.ext.int
import org.jacodb.api.jvm.ext.objectType
import org.jacodb.api.jvm.ext.toType
import org.usvm.jvm.util.toJcType
import org.usvm.machine.interpreter.transformers.JcSingleInstructionTransformer.BlockGenerationContext
import util.Relation
import util.TableInfo

abstract class JcDataclassLambdaTransformer(
    val cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    val subTable: TableInfo.TableWithIdInfo
) : JcBodyFillerFeature() {
    val clazz = cp.findClass(classTable.origClass.name)
    val classType = clazz.toType()
    val thisVal = JcThis(classType)
    val idType = classTable.idColumn.type.toJcType(cp)!!
    val idField = classTable.idColumn.origField

    val subClass = cp.findClass(subTable.origClass.name)
    val subIdType = subTable.idColumn.type.toJcType(cp)!!
    val subType = subClass.toType()
}

// Boolean filter(Subcl s) { return id == s.oneToMany_id }
class JcOneToManyFilterTransformer(
    val dataclassTransformer: JcDataclassTransformer,
    cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    subTable: TableInfo.TableWithIdInfo,
    val rel: Relation
) : JcDataclassLambdaTransformer(cp, classTable, subTable) {

    override fun condition(method: JcMethod): Boolean {
        return method.generatedOneToManyFilter
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

        val idVar = generateStaticCall("id", STATIC_GET_ID_NAME, classType, listOf(thisVal))

        val inst = method.parameters.single().toArgument
        val relField = dataclassTransformer.relationChecks.get(subTable.origClass, rel.origField)
        val relVar = generateVirtualCall("rel_val", getterName(relField), subType, inst, listOf())

        val res = generateIsEqual(cp, "res", idVar, relVar)

        addInstruction { loc -> JcReturnInst(loc, res) }
    }
}

// Boolean filter(Subcl s) { return s.$getId() == oneToMany_id; }
class JcManyToOneFilterTransformer(
    val dataclassTransformer: JcDataclassTransformer,
    cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    subTable: TableInfo.TableWithIdInfo,
    val rel: Relation
) : JcDataclassLambdaTransformer(cp, classTable, subTable) {

    override fun condition(method: JcMethod): Boolean {
        return method.generatedManyToOneFilter
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {
        val inst = method.parameters.single().toArgument
        val idVar = generateStaticCall("sub_id", STATIC_GET_ID_NAME, subType, listOf(inst))

        val relField = dataclassTransformer.relationChecks.get(clazz, rel.origField)
        val relVar = generateVirtualCall("rel_val", getterName(relField), classType, thisVal, listOf())

        val res = generateIsEqual(cp, "res", idVar, relVar)

        addInstruction { loc -> JcReturnInst(loc, res) }
    }
}

// Boolean betweenFilter(Object[] row) { return row[0] == id; }
class JcBtwFilterTransformer(
    cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    subTable: TableInfo.TableWithIdInfo,
    val btwTable: TableInfo?
) : JcDataclassLambdaTransformer(cp, classTable, subTable) {

    override fun condition(method: JcMethod): Boolean {
        return method.generatedBtwFilter
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

        val rowId = nextLocalVar("id_row", cp.objectType)
        val ix = btwTable!!.indexOfField(idField)
        val access = JcArrayAccess(method.parameters.first().toArgument, JcInt(ix, cp.int), cp.objectType)
        addInstruction { loc -> JcAssignInst(loc, rowId, access) }

        val idVar = generateStaticCall("id", STATIC_GET_ID_NAME, classType, listOf(thisVal))

        val res = generateIsEqual(cp, "res", rowId, idVar)

        addInstruction { loc -> JcReturnInst(loc, res) }
    }
}


// Integer betweenSelector(Object[] row) { return (Integer) row[1]; }
class JcBtwSelectTransformer(
    cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    subTable: TableInfo.TableWithIdInfo,
    val btwTable: TableInfo?
) : JcDataclassLambdaTransformer(cp, classTable, subTable) {

    override fun condition(method: JcMethod): Boolean {
        return method.generatedBtwSelect
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

        val rowSel = nextLocalVar("row_sel", cp.objectType)
        val ix = btwTable!!.indexOfField(subTable.idColumn.origField)
        val access = JcArrayAccess(method.parameters.first().toArgument, JcInt(ix, cp.int), cp.objectType)
        addInstruction { loc -> JcAssignInst(loc, rowSel, access) }

        val castVar = nextLocalVar("casted", subIdType)
        val cast = JcCastExpr(subIdType, rowSel)
        addInstruction { loc -> JcAssignInst(loc, castVar, cast) }

        addInstruction { loc -> JcReturnInst(loc, castVar) }
    }
}


// Boolean setFilter(Subcl s) { return mtmSet.contains(s.$getId()); }
class JcSetFilterTransformer(
    val dataclassTransformer: JcDataclassTransformer,
    cp: JcClasspath,
    classTable: TableInfo.TableWithIdInfo,
    subTable: TableInfo.TableWithIdInfo,
    val rel: Relation
) : JcDataclassLambdaTransformer(cp, classTable, subTable) {

    override fun condition(method: JcMethod): Boolean {
        return method.generatedSetFilter
    }

    override fun BlockGenerationContext.generateBody(method: JcMethod) {

        val setField = dataclassTransformer.relationSets.get(clazz, rel.origField)
        val setVar = generateVirtualCall("set_val", getterName(setField), classType, thisVal, listOf())

        val inst = method.parameters.first().toArgument
        val argIdVar = generateStaticCall("arg_id", STATIC_GET_ID_NAME, subType, listOf(inst))

        val conVar = generateVirtualCall(
            "contains",
            "contains",
            cp.findType(JAVA_SET) as JcClassType,
            setVar,
            listOf(argIdVar)
        )

        val res = toBoolean(cp, conVar)

        addInstruction { loc -> JcReturnInst(loc, res) }
    }
}
