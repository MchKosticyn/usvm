package machine.interpreter.transformers.springjpa.query.table

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.Select
import util.database.TableInfo

// ... FROM (SELECT ...) AS sub
class TableSubquery(val subquery: Select, alias: String?) : Table(alias) {
    override fun getAlisas(info: CommonInfo): Pair<String, String>? {
        return alias?.let { it to info.names.getQueryName() }
    }

    override fun collectNames(info: CommonInfo): Map<String, List<JcField>> {
        TODO("Not yet implemented")
    }

    override fun genInst(ctx: MethodCtx): JcLocalVar {
        TODO("Not yet implemented")
    }

    override fun getTbl(info: CommonInfo): TableInfo.TableWithIdInfo {
        TODO("Not yet implemented")
    }
}
