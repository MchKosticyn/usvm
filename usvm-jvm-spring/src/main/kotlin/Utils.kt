import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.api.jvm.ext.isSubClassOf

internal val JcClassOrInterface.isSpringFilter: Boolean
    get() {
        val filterType = classpath.findClassOrNull("jakarta.servlet.Filter")
            ?: return false
        return isSubClassOf(filterType)
    }

internal val JcClassOrInterface.isSpringFilterChain: Boolean
    get() {
        val filterType = classpath.findClassOrNull("jakarta.servlet.FilterChain")
            ?: return false
        return isSubClassOf(filterType)
    }

internal val JcClassOrInterface.isSpringHandlerInterceptor: Boolean
    get() {
        val filterType = classpath.findClassOrNull("org.springframework.web.servlet.HandlerInterceptor")
            ?: return false
        return isSubClassOf(filterType)
    }

internal val JcClassOrInterface.isSpringController: Boolean
    get() = annotations.any {
        it.name == "org.springframework.stereotype.Controller"
                || it.name == "org.springframework.web.bind.annotation.RestController"
    }

internal val JcClassOrInterface.isArgumentResolver: Boolean
    get() {
        val argumentResolverType =
            classpath.findClassOrNull("org.springframework.web.method.support.HandlerMethodArgumentResolver")
                ?: return false
        return isSubClassOf(argumentResolverType)
    }

internal val JcClassOrInterface.isSpringRepository: Boolean
    get() = this.annotations.any { it.name == "org.springframework.stereotype.Repository" }
            || classpath.findClassOrNull("org.springframework.data.repository.Repository")
                ?.let { isSubClassOf(it) } ?: false

internal val JcClassOrInterface.isSpringRequest: Boolean
    get() = classpath.findClassOrNull("jakarta.servlet.http.HttpServletRequest")
        ?.let { this.isSubClassOf(it) } ?: false

internal val JcClassOrInterface.isServletWebRequest: Boolean
    get() = classpath.findClassOrNull("org.springframework.web.context.request.ServletWebRequest")
        ?.let { this.isSubClassOf(it) } ?: false

internal val JcMethod.isSpringFilterMethod: Boolean
    get() = enclosingClass.isSpringFilter && (name == "doFilter" || name == "doFilterInternal")

internal val JcMethod.isSpringFilterChainMethod: Boolean
    get() = enclosingClass.isSpringFilterChain && (name == "doFilter" || name == "doFilterInternal")

internal val JcMethod.isArgumentResolverMethod: Boolean
    get() = enclosingClass.isArgumentResolver && (name == "resolveArgument" || name == "readWithMessageConverters" || name == "resolveName" || name == "handleNullValue")

internal val JcMethod.isHttpRequestMethod: Boolean
    get() = enclosingClass.isSpringRequest

internal val JcMethod.isServletRequestMethod: Boolean
    get() = enclosingClass.isServletWebRequest

internal val JcMethod.isDeserializationMethod: Boolean
    get() = name == "readWithMessageConverters"
            && enclosingClass.name == "org.springframework.web.servlet.mvc.method.annotation.RequestResponseBodyMethodProcessor"

internal val JcField.isInjectedViaValue: Boolean
    get() = !isStatic && annotations.any {
        it.name == "org.springframework.beans.factory.annotation.Value" && it.values.values.singleOrNull()?.let { value ->
            (value as? String)?.contains('$') == true
                    // TODO: support SpEL
                    && (value as? String)?.contains('#') == false
        } == true
    }
