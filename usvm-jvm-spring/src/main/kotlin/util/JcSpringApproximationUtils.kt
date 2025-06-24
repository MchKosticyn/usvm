package util

import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathFeature
import org.jacodb.api.jvm.JcDatabase
import org.usvm.machine.logger
import org.usvm.util.classpathWithApproximations
import java.io.File

private const val USVM_SPRING_API_JAR_PATH = "usvm.jvm.spring.api.jar.path"
private const val USVM_SPRING_APPROXIMATIONS_JAR_PATH = "usvm.jvm.spring.approximations.jar.path"

suspend fun JcDatabase.classpathWithSpringApproximations(
    dirOrJars: List<File>,
    features: List<JcClasspathFeature> = emptyList()
): JcClasspath {
    val usvmSpringApiJarPath = System.getenv(USVM_SPRING_API_JAR_PATH)
    val usvmSpringApproximationsJarPath = System.getenv(USVM_SPRING_APPROXIMATIONS_JAR_PATH)

    check(usvmSpringApiJarPath != null && usvmSpringApproximationsJarPath != null) {
        "classpathWithSpringApproximations: unable to find spring approximations paths"
    }

    logger.info { "Load USVM SPRING API: $usvmSpringApiJarPath" }
    logger.info { "Load USVM SPRING Approximations: $usvmSpringApproximationsJarPath" }

    val springApproximationsPath = listOf(File(usvmSpringApiJarPath), File(usvmSpringApproximationsJarPath))
    return this.classpathWithApproximations(dirOrJars, features, springApproximationsPath)
}
