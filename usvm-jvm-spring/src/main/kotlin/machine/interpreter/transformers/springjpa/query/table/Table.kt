package machine.interpreter.transformers.springjpa.query.table

import machine.interpreter.transformers.springjpa.query.CommonInfo
import machine.interpreter.transformers.springjpa.query.MethodCtx
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.cfg.JcLocalVar
import util.database.TableInfo

abstract class Table(
    val alias: String?
) {
    abstract fun getAlisas(info: CommonInfo): Pair<String, String>?
    abstract fun genLambas(): List<JcMethod>
    abstract fun collectNames(info: CommonInfo): Map<String, List<JcField>>
    abstract fun genInst(ctx: MethodCtx): JcLocalVar
    abstract fun getTbl(info: CommonInfo): TableInfo.TableWithIdInfo
}
