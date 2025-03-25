import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcField
import org.jacodb.api.jvm.JcMethod
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

internal val JcMethod.isSpringFilterMethod: Boolean
    get() = enclosingClass.isSpringFilter && (name == "doFilter" || name == "doFilterInternal")

internal val JcMethod.isSpringFilterChainMethod: Boolean
    get() = enclosingClass.isSpringFilterChain && (name == "doFilter" || name == "doFilterInternal")

internal val JcMethod.isArgumentResolverMethod: Boolean
    get() = enclosingClass.isArgumentResolver && (name == "resolveArgument" || name == "readWithMessageConverters" || name == "resolveName")

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
