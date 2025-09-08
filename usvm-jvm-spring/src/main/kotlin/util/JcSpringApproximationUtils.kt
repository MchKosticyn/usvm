package util

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathFeature
import org.jacodb.api.jvm.JcDatabase
import org.usvm.machine.logger
import org.usvm.util.ApproximationPaths
import org.usvm.util.classpathWithApproximations
import java.io.File

private const val USVM_SPRING_API_JAR_PATH = "usvm.jvm.spring.api.jar.path"
private const val USVM_SPRING_APPROXIMATIONS_JAR_PATH = "usvm.jvm.spring.approximations.jar.path"

class SpringApproximationPaths(
    usvmSpringApiPath: String? = null,
    usvmSpringApproximationsPath: String? = null,
    usvmApiJarPath: String? = null,
    usvmApproximationsJarPath: String? = null,
) : ApproximationPaths(usvmApiJarPath, usvmApproximationsJarPath) {

    val usvmSpringApiJarPath: String? =
        usvmSpringApiPath ?: System.getenv(USVM_SPRING_API_JAR_PATH)
    val usvmSpringApproximationsJarPath: String? =
        usvmSpringApproximationsPath ?: System.getenv(USVM_SPRING_APPROXIMATIONS_JAR_PATH)

    override val namedPaths = super.namedPaths + mapOf(
        "USVM Spring API" to usvmSpringApiJarPath,
        "USVM Spring Approximations" to usvmSpringApproximationsJarPath
    )
}

suspend fun JcDatabase.classpathWithSpringApproximations(
    dirOrJars: List<File>,
    features: List<JcClasspathFeature> = emptyList(),
    springApproximationPaths: SpringApproximationPaths = SpringApproximationPaths()
): JcClasspath {
    check(springApproximationPaths.allPathsArePresent) {
        "classpathWithSpringApproximations: unable to find spring approximations paths"
    }

    logger.info { "Load USVM SPRING API: ${springApproximationPaths.usvmSpringApiJarPath}" }
    logger.info { "Load USVM SPRING Approximations: ${springApproximationPaths.usvmSpringApproximationsJarPath}" }

    return this.classpathWithApproximations(dirOrJars, features, springApproximationPaths)
}
