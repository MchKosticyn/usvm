package features

import org.jacodb.api.jvm.JcInstExtFeature
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcRawCallInst
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.api.jvm.cfg.JcRawReturnInst
import org.jacodb.api.jvm.cfg.JcRawStaticCallExpr
import org.jacodb.impl.cfg.JcRawString
import org.usvm.concrete.api.internal.InitHelper
import org.usvm.util.javaName
import utils.isInternalType
import utils.isLambda
import utils.typeName

object JcClinitFeature: JcInstExtFeature {

    private fun shouldNotTransform(method: JcMethod, list: JcInstList<JcRawInst>): Boolean {
        return !method.isClassInitializer
                || list.size == 0
                || method.enclosingClass.declaration.location.isRuntime
                || method.enclosingClass.isInternalType
                || method.enclosingClass.name == InitHelper::class.java.name
                || method.enclosingClass.isLambda
                || method.enclosingClass.isSynthetic
    }

    override fun transformRawInstList(method: JcMethod, list: JcInstList<JcRawInst>): JcInstList<JcRawInst> {
        if (shouldNotTransform(method, list))
            return list

        val mutableList = list.toMutableList()
        val callExpr = JcRawStaticCallExpr(
            declaringClass = InitHelper::class.java.name.typeName,
            methodName = InitHelper::afterClinit.javaName,
            argumentTypes = listOf("java.lang.String".typeName),
            returnType = PredefinedPrimitives.Void.typeName,
            args = listOf(JcRawString(method.enclosingClass.name))
        )

        val returnStmts = mutableList.filterIsInstance<JcRawReturnInst>()
        for (returnStmt in returnStmts) {
            val callInst = JcRawCallInst(method, callExpr)
            mutableList.insertBefore(returnStmt, callInst)
        }

        return mutableList
    }
}
