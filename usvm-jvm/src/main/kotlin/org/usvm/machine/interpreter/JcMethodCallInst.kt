package org.usvm.machine.interpreter

import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.UExpr
import org.usvm.machine.JcMethodCall
import org.usvm.machine.JcMethodCallBaseInst

// Used to skip re-processing of stmts that require ensureExprCorrectness call
// see dispatchMakeCommonSymbolic
data class JcMethodCallSkipWithEnsureInst(
    val returnExpr: UExpr<*>,
    val methodCall: JcMethodCall,
    val type: JcType
) : JcMethodCallBaseInst, JcMethodCall {
    override val location = methodCall.location
    override val method = methodCall.method
    override val arguments = methodCall.arguments
    override val returnSite = methodCall.returnSite
    override val originalInst: JcInst = returnSite
}
