package org.usvm.util

import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathFeature
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.approximation.Approximations
import org.jacodb.impl.types.JcClassTypeImpl
import org.usvm.machine.logger
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private const val USVM_API_JAR_PATH = "usvm.jvm.api.jar.path"
private const val USVM_APPROXIMATIONS_JAR_PATH = "usvm.jvm.approximations.jar.path"

open class ApproximationPaths(
    usvmApiPath: String? = null,
    usvmApproximationsPath: String? = null
) {
    val usvmApiJarPath: String? = usvmApiPath ?: System.getenv(USVM_API_JAR_PATH)
    val usvmApproximationsJarPath: String? = usvmApproximationsPath ?: System.getenv(USVM_APPROXIMATIONS_JAR_PATH)

    open val namedPaths = mapOf(
        "USVM API" to usvmApiJarPath,
        "USVM Approximations" to usvmApproximationsJarPath
    )
    val presentPaths: Set<String> get() = namedPaths.values.filterNotNull().toSet()
    val allPathsArePresent get() = namedPaths.values.all { it != null }
}

private val classpathApproximations: MutableMap<JcClasspath, Set<String>> = ConcurrentHashMap()

// TODO: use another way to detect internal classes (e.g. special bytecode location type)
val JcClassOrInterface.isUsvmInternalClass: Boolean
    get() = classpathApproximations[classpath]?.contains(name) ?: false

val JcClassType.isUsvmInternalClass: Boolean
    get() = if (this is JcClassTypeImpl) {
        classpathApproximations[classpath]?.contains(name) ?: false
    } else {
        jcClass.isUsvmInternalClass
    }

suspend fun JcDatabase.classpathWithApproximations(
    dirOrJars: List<File>,
    features: List<JcClasspathFeature> = emptyList(),
    approximationPaths: ApproximationPaths = ApproximationPaths(),
): JcClasspath {
    if (!approximationPaths.allPathsArePresent)
        return classpath(dirOrJars, features)

    logger.info { "Load USVM API: ${approximationPaths.usvmApiJarPath}" }
    logger.info { "Load USVM Approximations: ${approximationPaths.usvmApproximationsJarPath}" }

    val approximationsPath = approximationPaths.presentPaths.map { File(it) }

    val cpWithApproximations = dirOrJars + approximationsPath
    val approximations = this.features.filterIsInstance<Approximations>().singleOrNull()
        ?: error("approximations feature not found in database features")
    val featuresWithApproximations = features + listOf(approximations)
    val cp = classpath(cpWithApproximations, featuresWithApproximations.distinct())

    val approximationsLocations = cp.locations.filter { it.jarOrFolder in approximationsPath }
    val approximationsClasses = approximationsLocations.flatMapTo(hashSetOf()) { it.classNames ?: emptySet() }
    classpathApproximations[cp] = approximationsClasses

    return cp
}
