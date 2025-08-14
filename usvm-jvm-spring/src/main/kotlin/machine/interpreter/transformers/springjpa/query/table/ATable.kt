package machine.interpreter.transformers.springjpa.query.table

import jpa.TableInfo
import jpa.generateGlobalTableAccessWithDataRow
import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.jacodb.api.jvm.ext.findClass
import org.usvm.spring.query.table.ATable
import org.usvm.spring.query.table.ITableVisitor
import org.usvm.spring.query.table.TableRoot
import org.usvm.spring.query.table.TableSubquery

fun ATable.getAlias(info: CommonInfo): Pair<String, String>? = this.accept(tableGetAliasVisitor, info)
private val tableGetAliasVisitor = object : ITableVisitor<Pair<String, String>?, CommonInfo> {
    override fun visit(child: TableRoot, ctx: CommonInfo) = with(child) { alias?.let { it to entityName.name() } }

    override fun visit(child: TableSubquery, ctx: CommonInfo) = with(child) {
        alias?.let { it to ctx.names.getQueryName() }
    }
}

fun ATable.collectNames(info: CommonInfo): Map<String, List<JcField>> = this.accept(tableCollectNamesVisitor, info)
private val tableCollectNamesVisitor = object : ITableVisitor<Map<String, List<JcField>>, CommonInfo> {
    override fun visit(child: TableRoot, ctx: CommonInfo) = with(child) {
        mapOf(entityName.name() to getTbl(ctx).columnsInOrder().map { it.origField })
    }

    override fun visit(child: TableSubquery, ctx: CommonInfo): Map<String, List<JcField>> {
        TODO("Not yet implemented")
    }
}

fun ATable.genInst(ctx: MethodCtx): JcLocalVar = this.accept(tableGenInstVisitor, ctx)
private val tableGenInstVisitor = object : ITableVisitor<JcLocalVar, MethodCtx> {
    override fun visit(child: TableRoot, ctx: MethodCtx) = with(ctx) {
        val classTable = child.getTbl(common)
        val varName = "${getVarName()}_${classTable.name}"
        val origClass = cp.findClass(classTable.origClassName)
        genCtx.generateGlobalTableAccessWithDataRow(cp, varName, classTable.name, origClass, child.alias!!)
    }

    override fun visit(child: TableSubquery, ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

}

fun ATable.getTbl(info: CommonInfo): TableInfo.TableWithIdInfo = this.accept(tableGetTblVisitor, info)
private val tableGetTblVisitor = object : ITableVisitor<TableInfo.TableWithIdInfo, CommonInfo> {
    override fun visit(child: TableRoot, ctx: CommonInfo) = with(child) {
        ctx.collector.getTableByPartName(entityName.name()).single()
    }

    override fun visit(child: TableSubquery, ctx: CommonInfo): TableInfo.TableWithIdInfo {
        TODO("Not yet implemented")
    }

}

fun ATable.getLambdas(info: CommonInfo) = emptyList<JcMethod>()
