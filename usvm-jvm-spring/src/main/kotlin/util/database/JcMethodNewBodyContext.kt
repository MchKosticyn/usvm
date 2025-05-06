package util.database

import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.TypeName
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.api.jvm.cfg.JcRawLocalVar
import org.jacodb.api.jvm.cfg.JcRawReturnInst

class JcMethodNewBodyContext(
    val owner: JcMethod
) {
    private val rawInst = owner.rawInstList.toMutableList()
        .also { insts ->
            insts.removeAll(
                insts.filter { it !is JcRawReturnInst }
            )
        }
    private val retInst = rawInst.single()
    private val localsManager = LocalVarsManager(0)

    fun newVar(typ: TypeName): JcRawLocalVar = localsManager.newLocalVar(typ)

    fun addInstruction(body: (JcMethod) -> JcRawInst) = rawInst.insertBefore(retInst, body(owner))

    class LocalVarsManager {

        private var lastIndex: Int

        constructor(startIndex: Int) {
            lastIndex = if (startIndex < 0) 0 else startIndex
        }

        private fun newName() = "%${lastIndex}"

        fun newLocalVar(typ: TypeName) = JcRawLocalVar(lastIndex++, newName(), typ)
    }

    fun buildNewBody(): JcInstList<JcRawInst> = rawInst
}
