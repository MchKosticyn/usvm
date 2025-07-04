package machine.state.memory

import machine.state.concreteMemory.JcConcreteMemory
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.humanReadableSignature
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UTypeConstraints
import org.usvm.machine.JcContext
import util.isSecurityExpressionRootMethod
import util.isArgumentResolverMethod
import util.isDeserializationMethod
import util.isHttpRequestMethod
import util.isServletRequestMethod
import util.isSpringFilterChainMethod
import util.isSpringFilterMethod

class JcSpringMemory(
    ctx: JcContext,
    ownership: MutabilityOwnership,
    typeConstraints: UTypeConstraints<JcType>,
) : JcConcreteMemory(
    ctx,
    ownership,
    typeConstraints,
) {

    override fun shouldNotInvoke(method: JcMethod): Boolean {
        return super.shouldNotInvoke(method) ||
                forbiddenInvocations.contains(method.humanReadableSignature) ||
                method.isSpringFilterMethod ||
                method.isSpringFilterChainMethod ||
                method.isArgumentResolverMethod ||
                method.isHttpRequestMethod ||
                method.isServletRequestMethod ||
                method.isDeserializationMethod ||
                method.isSecurityExpressionRootMethod
    }

    override fun shouldConcretizeMethod(method: JcMethod): Boolean {
        return super.shouldConcretizeMethod(method) || concretizeInvocations.contains(method.humanReadableSignature)
    }

    companion object {

        //region Concrete Invocations

        private val forbiddenInvocations = setOf(
            "org.springframework.test.web.servlet.MockMvc#perform(org.springframework.test.web.servlet.RequestBuilder):org.springframework.test.web.servlet.ResultActions",
            "org.springframework.mock.web.MockFilterChain#doFilter(jakarta.servlet.ServletRequest,jakarta.servlet.ServletResponse):void",
            "org.springframework.web.filter.RequestContextFilter#doFilterInternal(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,jakarta.servlet.FilterChain):void",
            "org.springframework.web.filter.FormContentFilter#doFilterInternal(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,jakarta.servlet.FilterChain):void",
            "org.springframework.web.filter.CharacterEncodingFilter#doFilterInternal(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,jakarta.servlet.FilterChain):void",
            "org.springframework.mock.web.MockFilterChain\$ServletFilterProxy#doFilter(jakarta.servlet.ServletRequest,jakarta.servlet.ServletResponse,jakarta.servlet.FilterChain):void",
            "jakarta.servlet.http.HttpServlet#service(jakarta.servlet.ServletRequest,jakarta.servlet.ServletResponse):void",
            "org.springframework.test.web.servlet.TestDispatcherServlet#service(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.FrameworkServlet#service(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "jakarta.servlet.http.HttpServlet#service(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",

            "org.springframework.web.servlet.FrameworkServlet#doGet(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.FrameworkServlet#doPost(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.FrameworkServlet#doPut(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.FrameworkServlet#doDelete(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.FrameworkServlet#doOptions(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.FrameworkServlet#doTrace(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",

            "org.springframework.web.servlet.FrameworkServlet#processRequest(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.DispatcherServlet#doService(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.DispatcherServlet#doDispatch(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse):void",
            "org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter#handle(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,java.lang.Object):org.springframework.web.servlet.ModelAndView",
            "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter#handleInternal(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,org.springframework.web.method.HandlerMethod):org.springframework.web.servlet.ModelAndView",
            "org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter#invokeHandlerMethod(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,org.springframework.web.method.HandlerMethod):org.springframework.web.servlet.ModelAndView",
            "org.springframework.web.method.annotation.ModelFactory#initModel(org.springframework.web.context.request.NativeWebRequest,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.method.HandlerMethod):void",
            "org.springframework.web.method.annotation.ModelFactory#invokeModelAttributeMethods(org.springframework.web.context.request.NativeWebRequest,org.springframework.web.method.support.ModelAndViewContainer):void",
            "org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod#invokeAndHandle(org.springframework.web.context.request.ServletWebRequest,org.springframework.web.method.support.ModelAndViewContainer,java.lang.Object[]):void",
            "org.springframework.web.method.support.InvocableHandlerMethod#invokeForRequest(org.springframework.web.context.request.NativeWebRequest,org.springframework.web.method.support.ModelAndViewContainer,java.lang.Object[]):java.lang.Object",
            "org.springframework.web.method.support.InvocableHandlerMethod#getMethodArgumentValues(org.springframework.web.context.request.NativeWebRequest,org.springframework.web.method.support.ModelAndViewContainer,java.lang.Object[]):java.lang.Object[]",
            "org.springframework.web.method.support.InvocableHandlerMethod#doInvoke(java.lang.Object[]):java.lang.Object",

            "org.springframework.web.servlet.mvc.method.annotation.ServletModelAttributeMethodProcessor#bindRequestParameters(org.springframework.web.bind.WebDataBinder,org.springframework.web.context.request.NativeWebRequest):void",
            "org.springframework.web.bind.ServletRequestDataBinder#bind(jakarta.servlet.ServletRequest):void",
            "org.springframework.web.bind.WebDataBinder#doBind(org.springframework.beans.MutablePropertyValues):void",
            "org.springframework.validation.DataBinder#doBind(org.springframework.beans.MutablePropertyValues):void",
            "org.springframework.validation.AbstractBindingResult#getModel():java.util.Map",

            "org.springframework.util.function.ThrowingSupplier#get():java.lang.Object",
            "org.springframework.util.function.ThrowingSupplier#get(java.util.function.BiFunction):java.lang.Object",

            "org.springframework.web.servlet.handler.HandlerMappingIntrospector#lambda\$createCacheFilter\$3(jakarta.servlet.ServletRequest,jakarta.servlet.ServletResponse,jakarta.servlet.FilterChain):void",
            "org.springframework.security.web.FilterChainProxy#doFilterInternal(jakarta.servlet.ServletRequest,jakarta.servlet.ServletResponse,jakarta.servlet.FilterChain):void",
            "org.springframework.security.web.session.DisableEncodeUrlFilter#doFilterInternal(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,jakarta.servlet.FilterChain):void",
            "org.springframework.security.web.header.HeaderWriterFilter#doHeadersAfter(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,jakarta.servlet.FilterChain):void",

            "org.springframework.security.web.FilterChainProxy#lambda\$doFilterInternal\$3(org.springframework.security.web.firewall.FirewalledRequest,jakarta.servlet.FilterChain,jakarta.servlet.ServletRequest,jakarta.servlet.ServletResponse):void",
            "org.springframework.web.filter.DelegatingFilterProxy#invokeDelegate(jakarta.servlet.Filter,jakarta.servlet.ServletRequest,jakarta.servlet.ServletResponse,jakarta.servlet.FilterChain):void",

            "org.springframework.mock.web.MockHttpServletRequest#getParameterMap():java.util.Map",
            "org.springframework.mock.web.MockHttpServletRequest#_getHeaderMap():java.util.Map",
            "org.springframework.mock.web.MockHttpServletRequest#_getMatrixMap():java.util.Map",
            "org.springframework.mock.web.MockHttpServletRequest#getHeader(java.lang.String):java.lang.String",
            "org.springframework.mock.web.MockHttpServletRequest#getParameter(java.lang.String):java.lang.String",

            "org.springframework.http.HttpHeaders#getContentType():org.springframework.http.MediaType",

            "org.springframework.security.web.access.intercept.AuthorizationFilter#getAuthentication():org.springframework.security.core.Authentication",
            "org.springframework.security.core.context.ThreadLocalSecurityContextHolderStrategy#getContext():org.springframework.security.core.context.SecurityContext",
            "org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors\$SecurityContextRequestPostProcessorSupport\$TestSecurityContextRepository#loadContext(org.springframework.security.web.context.HttpRequestResponseHolder):org.springframework.security.core.context.SecurityContext",
            "org.springframework.security.web.context.DelegatingSecurityContextRepository#loadContext(org.springframework.security.web.context.HttpRequestResponseHolder):org.springframework.security.core.context.SecurityContext",
            "org.springframework.security.web.context.RequestAttributeSecurityContextRepository#loadContext(org.springframework.security.web.context.HttpRequestResponseHolder):org.springframework.security.core.context.SecurityContext",
            "org.springframework.security.core.context.ThreadLocalSecurityContextHolderStrategy#lambda\$getDeferredContext\$0(org.springframework.security.core.context.SecurityContext):org.springframework.security.core.context.SecurityContext",
            "org.springframework.security.core.context.ThreadLocalSecurityContextHolderStrategy#getDeferredContext():java.util.function.Supplier",
            "org.springframework.security.core.context.SecurityContextImpl#getAuthentication():org.springframework.security.core.Authentication",
            "org.springframework.util.function.SingletonSupplier#get():java.lang.Object",
            "org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor#getAuthentication():org.springframework.security.core.Authentication",
            "org.springframework.security.authorization.AuthorizationManager#authorize(java.util.function.Supplier,java.lang.Object):org.springframework.security.authorization.AuthorizationResult",
            "org.springframework.security.web.access.intercept.RequestMatcherDelegatingAuthorizationManager#check(java.util.function.Supplier,java.lang.Object):org.springframework.security.authorization.AuthorizationDecision",
            "java.lang.ThreadLocal#get():java.lang.Object",

            "org.springframework.web.servlet.mvc.method.annotation.AbstractMessageConverterMethodArgumentResolver\$EmptyBodyCheckingHttpInputMessage#hasBody():boolean",
            "org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter#read(java.lang.reflect.Type,java.lang.Class,org.springframework.http.HttpInputMessage):java.lang.Object",
            "org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter#readJavaType(com.fasterxml.jackson.databind.JavaType,org.springframework.http.HttpInputMessage):java.lang.Object",
            "com.fasterxml.jackson.databind.ObjectReader#readValue(java.io.InputStream):java.lang.Object",
            "com.fasterxml.jackson.databind.ObjectReader#_bindAndClose(com.fasterxml.jackson.core.JsonParser):java.lang.Object",
            "com.fasterxml.jackson.databind.deser.DefaultDeserializationContext#readRootValue(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.JavaType,com.fasterxml.jackson.databind.JsonDeserializer,java.lang.Object):java.lang.Object",
            "com.fasterxml.jackson.databind.deser.BeanDeserializer#deserialize(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.DeserializationContext):java.lang.Object",
            "com.fasterxml.jackson.databind.deser.BeanDeserializer#deserializeFromObject(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.DeserializationContext):java.lang.Object",
            "com.fasterxml.jackson.databind.deser.impl.MethodProperty#deserializeAndSet(com.fasterxml.jackson.core.JsonParser,com.fasterxml.jackson.databind.DeserializationContext,java.lang.Object):void"
        )

        private val concretizeInvocations = setOf(
            "org.springframework.web.servlet.DispatcherServlet#processDispatchResult(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,org.springframework.web.servlet.HandlerExecutionChain,org.springframework.web.servlet.ModelAndView,java.lang.Exception):void",
            // TODO: need it? #CM
            "org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite#handleReturnValue(java.lang.Object,org.springframework.core.MethodParameter,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.context.request.NativeWebRequest):void",
        )

        //endregion

        //region Invariants check

        init {
            check(concretizeInvocations.intersect(forbiddenInvocations).isEmpty())
        }

        //endregion
    }
}
