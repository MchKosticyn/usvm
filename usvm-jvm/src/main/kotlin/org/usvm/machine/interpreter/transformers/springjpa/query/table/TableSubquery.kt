package org.usvm.machine.interpreter.transformers.springjpa.query.table

import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import org.usvm.machine.interpreter.transformers.springjpa.SelectCtx
import org.usvm.machine.interpreter.transformers.springjpa.query.CommonInfo
import org.usvm.machine.interpreter.transformers.springjpa.query.MethodCtx
import org.usvm.util.TableInfo

class TableSubquery {

    // ... FROM (SELECT ...) AS sub
    class TableSubqueryCtx(
        val subqury: SelectCtx,
        alias: String?
    ) : TableCtx(alias) {

        override fun getAlisas(info: CommonInfo): Pair<String, String>? {
            return alias?.let { it to info.names.getQueryName() }
        }

        override fun genLambas(): List<JcMethod> {
            TODO("Not yet implemented")
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
}
