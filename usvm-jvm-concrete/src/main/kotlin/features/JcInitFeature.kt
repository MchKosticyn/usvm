package features

import org.jacodb.api.jvm.JcInstExtFeature
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.PredefinedPrimitives
import org.jacodb.api.jvm.cfg.JcInstList
import org.jacodb.api.jvm.cfg.JcRawCallInst
import org.jacodb.api.jvm.cfg.JcRawInst
import org.jacodb.api.jvm.cfg.JcRawReturnInst
import org.jacodb.api.jvm.cfg.JcRawStaticCallExpr
import org.jacodb.api.jvm.cfg.JcRawThis
import org.usvm.concrete.api.internal.InitHelper
import utils.isLambda
import utils.javaName
import utils.notTracked
import utils.typeName

internal object JcInitFeature: JcInstExtFeature {

    private fun shouldNotTransform(method: JcMethod): Boolean {
        val type = method.enclosingClass
        return !method.isConstructor
                || type.isInterface
                || type.isAbstract
                || type.declaration.location.isRuntime
                || type.name == InitHelper::class.java.name
                || type.isLambda
                || type.isSynthetic
                || type.notTracked
    }

    override fun transformRawInstList(method: JcMethod, list: JcInstList<JcRawInst>): JcInstList<JcRawInst> {
        if (shouldNotTransform(method))
            return list

        val mutableList = list.toMutableList()
        val typeName = method.enclosingClass.name.typeName
        val callExpr = JcRawStaticCallExpr(
            declaringClass = InitHelper::class.java.name.typeName,
            methodName = InitHelper::afterInit.javaName,
            argumentTypes = listOf("java.lang.Object".typeName),
            returnType = PredefinedPrimitives.Void.typeName,
            args = listOf(JcRawThis(typeName))
        )

        val returnStmts = mutableList.filterIsInstance<JcRawReturnInst>()
        for (returnStmt in returnStmts) {
            val callInst = JcRawCallInst(method, callExpr)
            mutableList.insertBefore(returnStmt, callInst)
        }

        return mutableList
    }
}
