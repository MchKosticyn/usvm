package machine.state.memory

import kotlinx.coroutines.runBlocking
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.humanReadableSignature
import org.jacodb.impl.features.classpaths.JcUnknownClass
import org.jacodb.impl.features.hierarchyExt
import org.usvm.machine.JcContext

class JcForbiddenInvocations(val ctx: JcContext, val cp: JcClasspath) {
    private val forbiddenInvocations: Set<JcMethod> = initForbiddenInvocations()

    // For logging warnings of missing forbidden invocations
    private var section = "Initial"

    fun shouldNotInvoke(method: JcMethod): Boolean {
        return forbiddenInvocations.contains(method)
    }

    fun getSignatures(): Set<String> {
        return forbiddenInvocations.map { it.humanReadableSignature }.toSet()
    }

    private fun getDescriptions(): Set<JcMethod> {
        return setOf(
            section("Filter chains and servlet"),
            children("jakarta.servlet.FilterChain").methods("doFilter", "doFilterInternal"),
            children("jakarta.servlet.Filter").methods("doFilter", "doFilterInternal"),
            only("org.springframework.web.filter.DelegatingFilterProxy").methods("invokeDelegate"),
            only("org.springframework.web.servlet.handler.HandlerMappingIntrospector").methods("lambda\\\$createCacheFilter\\\$3"),
            only("org.springframework.security.web.FilterChainProxy").methods("lambda\\\$doFilterInternal\\\$3"),
            only("org.springframework.test.web.servlet.MockMvc").methods("perform"),
            only("jakarta.servlet.http.HttpServlet").methods("service"),
            only("org.springframework.web.servlet.FrameworkServlet").methods("service", "processRequest", "do.*"),
            only("org.springframework.test.web.servlet.TestDispatcherServlet").methods("service"),
            only("org.springframework.web.servlet.DispatcherServlet").methods("doService", "doDispatch"),
            children("org.springframework.web.servlet.mvc.method.AbstractHandlerMethodAdapter").methods("handle"),
            only("org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerAdapter").methods("handleInternal", "invokeHandlerMethod"),
            only("org.springframework.web.method.annotation.ModelFactory").methods("initModel", "invokeModelAttributeMethods"),
            only("org.springframework.web.servlet.mvc.method.annotation.ServletInvocableHandlerMethod").methods("invokeAndHandle"),
            only("org.springframework.web.method.support.InvocableHandlerMethod").methods("invokeForRequest", "getMethodArgumentValues", "doInvoke"),
            only("org.springframework.web.servlet.mvc.method.annotation.ServletModelAttributeMethodProcessor").methods("bindRequestParameters"),
            only("org.springframework.validation.AbstractBindingResult").methods("getModel"),
            only("org.springframework.util.function.ThrowingSupplier").methods("get"),
            section("Request methods"),
            only("org.springframework.security.web.header.HeaderWriterFilter").methods("doHeaders.*"),
            only("org.springframework.mock.web.MockHttpServletRequest").methods(all()),
            only("jakarta.servlet.http.HttpServletRequest").methods(all()),
            only("org.springframework.web.context.request.ServletWebRequest").methods(all()),
            only("org.springframework.http.HttpHeaders").methods("getContentType"), // TODO #AA: Maybe more here
            section("Security"),
            only("org.springframework.security.web.access.intercept.AuthorizationFilter").methods("getAuthentication"),
            only("org.springframework.security.core.context.ThreadLocalSecurityContextHolderStrategy").methods("getContext", "getDeferredContext"),
            only("org.springframework.security.core.context.ThreadLocalSecurityContextHolderStrategy").methods("lambda\\\$getDeferredContext\\\$0"),
            only("org.springframework.security.web.context.SecurityContextRepository").methods("loadContext", "loadDeferredContext"),
            children("org.springframework.security.core.context.SecurityContext").methods("getAuthentication"),
            only("org.springframework.util.function.SingletonSupplier").methods("get"),
            only("org.springframework.security.authorization.method.AuthorizationManagerBeforeMethodInterceptor").methods("getAuthentication"),
            children("org.springframework.security.authorization.AuthorizationManager").methods("authorize", "check"),
            children("org.springframework.security.web.ObservationFilterChainDecorator\$FilterObservation").methods(all()),
            children("org.springframework.security.web.ObservationFilterChainDecorator\$ObservationFilter").methods(all()),
            children("io.micrometer.observation.Observation\$Context").methods(all()),
            children("org.springframework.security.web.FilterChainProxy\$FilterChainDecorator").methods(all()),
            section("Data binding and JSON"),
            children("org.springframework.validation.DataBinder").methods("bind", "doBind"),
            only("org.springframework.web.servlet.mvc.method.annotation.AbstractMessageConverterMethodArgumentResolver\$EmptyBodyCheckingHttpInputMessage").methods("hasBody"),
            only("org.springframework.http.converter.json.AbstractJackson2HttpMessageConverter").methods("read", "readJavaType"),
            only("com.fasterxml.jackson.databind.ObjectReader").methods("readValue", "_bindAndClose"),
            only("com.fasterxml.jackson.databind.deser.DefaultDeserializationContext").methods("readRootValue"),
            only("com.fasterxml.jackson.databind.deser.BeanDeserializer").methods("deserialize", "deserializeFromObject"),
            only("com.fasterxml.jackson.databind.deser.impl.MethodProperty").methods("deserializeAndSet"),
            section("General purpose"),
            only("java.lang.ThreadLocal").methods("get"),
            section("Argument resolving"),
            children("org.springframework.web.method.support.HandlerMethodArgumentResolver")
                .methods("convertIfNecessary", "resolveArgument", "resolveName", "handleNullValue", "readWithMessageConverters")
        ).flatten().toSet()
    }

    private fun warn(message: String) {
        println("FI Warning [$section]: $message")
    }

    private fun initForbiddenInvocations(): Set<JcMethod> {
        val descriptions = getDescriptions()
        return descriptions
    }

    private fun children(parent: String): Set<JcClassOrInterface> {
        return runBlocking { ctx.cp.hierarchyExt() }
            .findSubClasses(parent, entireHierarchy = true, includeOwn = true)
            .toSet()
    }

    private fun only(name: String): Set<JcClassOrInterface> {
        val foundClass = cp.findClassOrNull(name)
        if (foundClass == null || foundClass is JcUnknownClass) {
            warn("Class not found $name")
            return setOf()
        }
        return setOf(foundClass)
    }

    private fun all(): String {
        return ".*"
    }

    private fun Set<JcClassOrInterface>.methods(vararg names: String): Set<JcMethod> {
        val foundMethods = this.flatMap { clazz ->
            clazz.declaredMethods.filter {
                    method -> names.any { name -> Regex(name).matches(method.name) }
            }
        }.toSet()

        if (foundMethods.isEmpty() || foundMethods.count() < names.count()) {
            val message = names.joinToString(", ")
            warn("Some methods were not found ($message)")
        }

        return foundMethods
    }

    private fun section(section: String): Set<JcMethod> {
        this.section = section
        return setOf()
    }
}
