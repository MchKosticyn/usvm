package machine

import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.JcTypedMethod
import org.jacodb.api.jvm.cfg.JcInst
import org.usvm.UExpr
import org.usvm.USort
import org.usvm.machine.JcMethodCall
import org.usvm.machine.JcMethodCallBaseInst

data class JcConcreteInvocationResult(
    val returnExpr: UExpr<USort>,
    private val methodCall: JcMethodCall
) : JcMethodCallBaseInst, JcMethodCall {
    override val location = methodCall.location
    override val method = methodCall.method
    override val arguments = methodCall.arguments
    override val returnSite = methodCall.returnSite
    override val originalInst: JcInst = returnSite
}

data class JcReflectionInvokeResult(
    private val methodCall: JcMethodCall,
    val invokeMethod: JcTypedMethod
) : JcMethodCallBaseInst, JcMethodCall {
    override val location = methodCall.location
    override val method = methodCall.method
    override val arguments = methodCall.arguments
    override val returnSite = methodCall.returnSite
    override val originalInst: JcInst = returnSite
}

data class JcMockMethodInvokeResult(
    private val methodCall: JcMethodCall
) : JcMethodCallBaseInst, JcMethodCall {
    override val location = methodCall.location
    override val method = methodCall.method
    override val arguments = methodCall.arguments
    override val returnSite = methodCall.returnSite
    override val originalInst: JcInst = returnSite
}

data class JcReflectionConstructorInvokeResult(
    private val methodCall: JcMethodCall,
    val invokeMethod: JcTypedMethod,
    val result: UExpr<out USort>
) : JcMethodCallBaseInst, JcMethodCall {
    override val location = methodCall.location
    override val method = methodCall.method
    override val arguments = methodCall.arguments
    override val returnSite = methodCall.returnSite
    override val originalInst: JcInst = returnSite
}

data class JcBoxMethodCall(
    private val methodCall: JcMethodCall,
    val resultExpr: UExpr<out USort>,
    val resultType: JcType,
) : JcMethodCallBaseInst, JcMethodCall {
    override val location = methodCall.location
    override val method = methodCall.method
    override val arguments = methodCall.arguments
    override val returnSite = methodCall.returnSite
    override val originalInst: JcInst = returnSite
}
