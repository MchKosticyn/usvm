package machine.state.memory

import machine.state.concreteMemory.JcConcreteMemory
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.humanReadableSignature
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UTypeConstraints
import org.usvm.machine.JcContext

class JcSpringMemory(
    ctx: JcContext,
    ownership: MutabilityOwnership,
    typeConstraints: UTypeConstraints<JcType>,
) : JcConcreteMemory(
    ctx,
    ownership,
    typeConstraints,
) {

    private val forbiddenInvocations = JcForbiddenInvocations(ctx, ctx.cp);

    override fun shouldNotInvoke(method: JcMethod): Boolean {
        return super.shouldNotInvoke(method)
                || forbiddenInvocations.shouldNotInvoke(method)
                || method.enclosingClass.name.contains("$\$SpringCGLIB$$")
    }

    override fun shouldConcretizeMethod(method: JcMethod): Boolean {
        return super.shouldConcretizeMethod(method) || concretizeInvocations.contains(method.humanReadableSignature)
    }

    companion object {

        //region Concrete Invocations

        private val concretizeInvocations = setOf(
            "org.springframework.web.servlet.DispatcherServlet#processDispatchResult(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,org.springframework.web.servlet.HandlerExecutionChain,org.springframework.web.servlet.ModelAndView,java.lang.Exception):void",
            // TODO: need it? #CM
            "org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite#handleReturnValue(java.lang.Object,org.springframework.core.MethodParameter,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.context.request.NativeWebRequest):void",
        )

        //endregion
    }

    //region Invariants check

    init {
        check(concretizeInvocations.intersect(forbiddenInvocations.getSignatures()).isEmpty())
    }

    //endregion
}
