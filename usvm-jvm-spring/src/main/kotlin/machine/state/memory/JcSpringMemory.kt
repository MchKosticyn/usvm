package machine.state.memory

import isSpringFilter
import isSpringFilterChain
import machine.concreteMemory.JcConcreteExecutor
import machine.concreteMemory.JcConcreteMemory
import machine.concreteMemory.JcConcreteMemoryBindings
import machine.concreteMemory.JcConcreteRegionStorage
import machine.concreteMemory.Marshall
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.JcType
import org.jacodb.api.jvm.ext.humanReadableSignature
import org.usvm.UIndexedMocker
import org.usvm.collections.immutable.implementations.immutableMap.UPersistentHashMap
import org.usvm.collections.immutable.internal.MutabilityOwnership
import org.usvm.constraints.UTypeConstraints
import org.usvm.machine.JcContext
import org.usvm.memory.UMemoryRegion
import org.usvm.memory.UMemoryRegionId
import org.usvm.memory.URegistersStack

class JcSpringMemory: JcConcreteMemory {

    constructor(
        ctx: JcContext,
        ownership: MutabilityOwnership,
        typeConstraints: UTypeConstraints<JcType>,
        stack: URegistersStack,
        mocks: UIndexedMocker<JcMethod>,
        regions: UPersistentHashMap<UMemoryRegionId<*, *>, UMemoryRegion<*, *>>,
        executor: JcConcreteExecutor,
        bindings: JcConcreteMemoryBindings,
        regionStorage: JcConcreteRegionStorage,
        marshall: Marshall
    ): super(
        ctx,
        ownership,
        typeConstraints,
        stack,
        mocks,
        regions,
        executor,
        bindings,
        regionStorage,
        marshall
    )

    constructor(
        ctx: JcContext,
        ownership: MutabilityOwnership,
        typeConstraints: UTypeConstraints<JcType>,
    ): super(
        ctx,
        ownership,
        typeConstraints
    )

    override fun shouldNotInvoke(method: JcMethod): Boolean {
        return super.shouldNotInvoke(method) ||
                forbiddenInvocations.contains(method.humanReadableSignature) ||
                method.enclosingClass.isSpringFilter && (method.name == "doFilter" || method.name == "doFilterInternal") ||
                method.enclosingClass.isSpringFilterChain && (method.name == "doFilter" || method.name == "doFilterInternal")
    }

    override fun createNewMemory(
        ctx: JcContext,
        ownership: MutabilityOwnership,
        typeConstraints: UTypeConstraints<JcType>,
        stack: URegistersStack,
        mocks: UIndexedMocker<JcMethod>,
        regions: UPersistentHashMap<UMemoryRegionId<*, *>, UMemoryRegion<*, *>>,
        executor: JcConcreteExecutor,
        bindings: JcConcreteMemoryBindings,
        regionStorage: JcConcreteRegionStorage,
        marshall: Marshall
    ): JcConcreteMemory {
        return JcSpringMemory(
            ctx,
            ownership,
            typeConstraints,
            stack,
            mocks,
            regions,
            executor,
            bindings,
            regionStorage,
            marshall
        )
    }

    companion object {

        //region Concrete Invocations

        private val forbiddenInvocations = setOf(
            "org.springframework.test.context.TestContextManager#<init>(java.lang.Class):void",
            "org.springframework.test.context.TestContextManager#<init>(org.springframework.test.context.TestContextBootstrapper):void",
            "org.springframework.boot.test.context.SpringBootTestContextBootstrapper#buildTestContext():org.springframework.test.context.TestContext",
            "org.springframework.test.context.support.AbstractTestContextBootstrapper#buildTestContext():org.springframework.test.context.TestContext",
            "org.springframework.test.context.support.AbstractTestContextBootstrapper#buildMergedContextConfiguration():org.springframework.test.context.MergedContextConfiguration",
            "org.springframework.test.context.support.AbstractTestContextBootstrapper#buildDefaultMergedContextConfiguration(java.lang.Class,org.springframework.test.context.CacheAwareContextLoaderDelegate):org.springframework.test.context.MergedContextConfiguration",
            "org.springframework.test.context.support.AbstractTestContextBootstrapper#buildMergedContextConfiguration(java.lang.Class,java.util.List,org.springframework.test.context.MergedContextConfiguration,org.springframework.test.context.CacheAwareContextLoaderDelegate,boolean):org.springframework.test.context.MergedContextConfiguration",
            "org.springframework.test.context.MergedContextConfiguration#<init>(java.lang.Class,java.lang.String[],java.lang.Class[],java.util.Set,java.lang.String[],java.util.List,java.lang.String[],java.util.Set,org.springframework.test.context.ContextLoader,org.springframework.test.context.CacheAwareContextLoaderDelegate,org.springframework.test.context.MergedContextConfiguration):void",
            "org.springframework.test.context.MergedContextConfiguration#processContextCustomizers(java.util.Set):java.util.Set",
            "org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTestContextBootstrapper#processMergedContextConfiguration(org.springframework.test.context.MergedContextConfiguration):org.springframework.test.context.MergedContextConfiguration",
            "org.springframework.boot.test.context.SpringBootTestContextBootstrapper#processMergedContextConfiguration(org.springframework.test.context.MergedContextConfiguration):org.springframework.test.context.MergedContextConfiguration",
            "org.springframework.boot.test.context.SpringBootTestContextBootstrapper#getOrFindConfigurationClasses(org.springframework.test.context.MergedContextConfiguration):java.lang.Class[]",
            "org.springframework.boot.test.context.SpringBootTestContextBootstrapper#findConfigurationClass(java.lang.Class):java.lang.Class",
            "org.springframework.boot.test.context.AnnotatedClassFinder#findFromClass(java.lang.Class):java.lang.Class",
            "org.springframework.test.context.TestContextManager#getTestContext():org.springframework.test.context.TestContext",
            "org.springframework.test.context.support.DefaultTestContext#getApplicationContext():org.springframework.context.ApplicationContext",
            "org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate#loadContext(org.springframework.test.context.MergedContextConfiguration):org.springframework.context.ApplicationContext",
            "org.springframework.test.context.cache.DefaultCacheAwareContextLoaderDelegate#loadContextInternal(org.springframework.test.context.MergedContextConfiguration):org.springframework.context.ApplicationContext",
            "org.springframework.boot.test.context.SpringBootContextLoader#loadContext(org.springframework.test.context.MergedContextConfiguration):org.springframework.context.ApplicationContext",
            "org.springframework.boot.test.context.SpringBootContextLoader#loadContext(org.springframework.test.context.MergedContextConfiguration,org.springframework.boot.test.context.SpringBootContextLoader\$Mode,org.springframework.context.ApplicationContextInitializer):org.springframework.context.ApplicationContext",
            "org.springframework.boot.test.context.SpringBootContextLoader\$ContextLoaderHook#<init>(org.springframework.boot.test.context.SpringBootContextLoader\$Mode,org.springframework.context.ApplicationContextInitializer,java.util.function.Consumer):void",
            "org.springframework.boot.test.context.SpringBootContextLoader\$ContextLoaderHook#run(org.springframework.util.function.ThrowingSupplier):org.springframework.context.ApplicationContext",
            "org.springframework.boot.test.context.SpringBootContextLoader#getSpringApplication():org.springframework.boot.SpringApplication",
            "org.springframework.boot.SpringApplication#withHook(org.springframework.boot.SpringApplicationHook,org.springframework.util.function.ThrowingSupplier):java.lang.Object",
            "org.springframework.boot.test.context.SpringBootContextLoader#lambda\$loadContext\$3(org.springframework.boot.SpringApplication,java.lang.String[]):org.springframework.context.ConfigurableApplicationContext",
            "org.springframework.boot.SpringApplication#run(java.lang.String[]):org.springframework.context.ConfigurableApplicationContext",
            "org.springframework.boot.SpringApplication#printBanner(org.springframework.core.env.ConfigurableEnvironment):org.springframework.boot.Banner",
            "org.springframework.boot.SpringApplication#afterRefresh(org.springframework.context.ConfigurableApplicationContext,org.springframework.boot.ApplicationArguments):void",
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
            "org.springframework.web.method.support.HandlerMethodArgumentResolverComposite#resolveArgument(org.springframework.core.MethodParameter,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.context.request.NativeWebRequest,org.springframework.web.bind.support.WebDataBinderFactory):java.lang.Object",
            "org.springframework.web.servlet.mvc.method.annotation.PathVariableMethodArgumentResolver#resolveArgument(org.springframework.core.MethodParameter,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.context.request.NativeWebRequest,org.springframework.web.bind.support.WebDataBinderFactory):java.lang.Object",
            "org.springframework.web.method.annotation.RequestParamMethodArgumentResolver#resolveArgument(org.springframework.core.MethodParameter,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.context.request.NativeWebRequest,org.springframework.web.bind.support.WebDataBinderFactory):java.lang.Object",
            "org.springframework.web.method.annotation.ModelAttributeMethodProcessor#resolveArgument(org.springframework.core.MethodParameter,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.context.request.NativeWebRequest,org.springframework.web.bind.support.WebDataBinderFactory):java.lang.Object",
            "org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor#resolveArgument(org.springframework.core.MethodParameter,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.context.request.NativeWebRequest,org.springframework.web.bind.support.WebDataBinderFactory):java.lang.Object",
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
        )

        private val concretizeInvocations = setOf(
            "org.springframework.web.servlet.DispatcherServlet#processDispatchResult(jakarta.servlet.http.HttpServletRequest,jakarta.servlet.http.HttpServletResponse,org.springframework.web.servlet.HandlerExecutionChain,org.springframework.web.servlet.ModelAndView,java.lang.Exception):void",
            // TODO: need it? #CM
            "org.springframework.web.method.support.HandlerMethodReturnValueHandlerComposite#handleReturnValue(java.lang.Object,org.springframework.core.MethodParameter,org.springframework.web.method.support.ModelAndViewContainer,org.springframework.web.context.request.NativeWebRequest):void",
            // TODO: delete #CM
            "org.usvm.samples.strings11.StringConcat#concretize():void",
        )

        //endregion

        //region Invariants check

        init {
            check(concretizeInvocations.intersect(forbiddenInvocations).isEmpty())
        }

        //endregion
    }
}
