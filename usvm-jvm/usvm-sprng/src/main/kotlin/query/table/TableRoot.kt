package machine.interpreter.transformers.springjpa.query.table

import machine.interpreter.transformers.springjpa.generateGlobalTableAccessWithDataRow
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.findClass
import util.database.TableInfo

// ... FROM SomeTable AS st
class TableRoot(
    val entityName: EntityNameCtx,
    alias: String?
) : Table(alias) {

    // Name of class (may contain points: java.lang.Boolean)
    // TODO: polymorphism like FROM java.lang.Object
    class EntityNameCtx(names: List<String>) {
        val name = names.joinToString(separator = ".")
        override fun toString() = name
    }

    override fun getAlisas(info: CommonInfo) = alias?.let { it to entityName.name }

    private var cachedTbl: TableInfo.TableWithIdInfo? = null
    override fun getTbl(info: CommonInfo): TableInfo.TableWithIdInfo {
        if (cachedTbl != null) return cachedTbl as TableInfo.TableWithIdInfo
        cachedTbl = info.collector.getTableByPartName(entityName.name).single() // it can be resolved
        return cachedTbl as TableInfo.TableWithIdInfo
    }

    override fun collectNames(info: CommonInfo): Map<String, List<JcField>> {
        return mapOf(entityName.name to getTbl(info).columnsInOrder().map { it.origField })
    }

    override fun genInst(ctx: MethodCtx): JcLocalVar = with(ctx) {
        val classTable = getTbl(common)
        val varName = "${getVarName()}_${classTable.name}"
        val origClass = cp.findClass(classTable.origClassName)
        return genCtx.generateGlobalTableAccessWithDataRow(cp, varName, classTable.name, origClass, alias!!)
    }
}
