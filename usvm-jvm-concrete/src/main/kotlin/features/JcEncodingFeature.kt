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
import org.jacodb.impl.types.TypeNameImpl
import org.usvm.concrete.api.internal.InitHelper
import org.usvm.jvm.util.javaName
import utils.isInternalType

object JcEncodingFeature: JcInstExtFeature {
    private fun shouldTransform(method: JcMethod): Boolean {
        val type = method.enclosingClass
        return method.isConstructor && type.isInternalType && !type.isAbstract
    }

    override fun transformRawInstList(method: JcMethod, list: JcInstList<JcRawInst>): JcInstList<JcRawInst> {
        if (!shouldTransform(method))
            return list

        val mutableList = list.toMutableList()
        val typeName = TypeNameImpl.fromTypeName(method.enclosingClass.name)
        val callExpr = JcRawStaticCallExpr(
            declaringClass = TypeNameImpl.fromTypeName(InitHelper::class.java.typeName),
            methodName = InitHelper::afterInternalInit.javaName,
            argumentTypes = listOf(TypeNameImpl.fromTypeName("java.lang.Object")),
            returnType = TypeNameImpl.fromTypeName(PredefinedPrimitives.Void),
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
