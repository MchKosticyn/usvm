package org.usvm.bench.project

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.jacodb.api.jvm.JcByteCodeLocation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcClasspathFeature
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.ext.findClass
import org.jacodb.approximation.Approximations
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.features.classpaths.UnknownClasses
import org.jacodb.impl.jacodb
import org.usvm.bench.project.Utils.projectFileName
import org.usvm.machine.interpreter.transformers.JcStringConcatTransformer
import org.usvm.util.classpathWithApproximations
import java.io.File
import java.io.FileNotFoundException
import java.nio.file.Path
import kotlin.io.path.exists

class Project private constructor(
    private val db: JcDatabase,
    val directory: String,
    private val projectJsonModel: ProjectJsonModel,
    private val cpFiles: List<File>,
    private val jcFeatures: List<JcClasspathFeature>,
    val javaHome: String?,
    val projectFiles: List<File>,
    val dependencyFiles: List<File>
) : AutoCloseable {

    val sourceRoot = projectJsonModel.sourceRoot

    val cpWithApproximations: JcClasspath by lazy {
        runBlocking {
            db.classpathWithApproximations(cpFiles, jcFeatures)
        }
    }

    val cp: JcClasspath by lazy {
        runBlocking {
            db.classpath(cpFiles, jcFeatures)
        }
    }

    private fun getClassesInternal(cp: JcClasspath, onlyTests: Boolean): Sequence<JcClassOrInterface> = sequence {
        projectJsonModel.classes
            .filter { !onlyTests || it.isTestModule }
            .forEach {
                it.classNames.forEach {
                    yield(cp.findClass(it))
                }
            }
    }

    fun getClasses(cp: JcClasspath) = getClassesInternal(cp, false)

    fun getTestClasses(cp: JcClasspath) = getClassesInternal(cp, true)

    override fun close() {
        cp.close()
        db.close()
    }

    companion object {

        suspend fun fromDir(
            projectDir: Path,
            additionalFeatures: List<JcClasspathFeature> = emptyList()
        ): Project {
            val projectFilePath = projectDir.resolve(projectFileName)
            if (!projectFilePath.exists()) {
                throw FileNotFoundException("No $projectFileName file found in the project directory")
            }

            val projectJsonString = projectFilePath.toFile().readText()
            val projectJsonModel = Json.decodeFromString<ProjectJsonModel>(projectJsonString)

            val dependencyFiles = projectJsonModel.dependencyFiles.map(::File)
            val projectModulesFiles = projectJsonModel.classes
                .map { File(it.registeredLocation) }
                .distinct()

            val allCpFiles = mutableListOf<File>()
            allCpFiles.addAll(projectModulesFiles)
            allCpFiles.addAll(dependencyFiles)

            val db = jacodb {
                val javaHome = projectJsonModel.javaHome
                if (javaHome != null) {
                    useJavaRuntime(File(javaHome))
                } else {
                    useProcessJavaRuntime()
                }

                //persistenceImpl(JcRamErsSettings)

                installFeatures(InMemoryHierarchy)
                installFeatures(Usages)
                //installFeatures(ClassScorer(TypeScorer, ::scoreClassNode))
                installFeatures(Approximations)

                loadByteCode(allCpFiles)
            }

            db.awaitBackgroundJobs()

            val features = mutableListOf<JcClasspathFeature>(UnknownClasses, JcStringConcatTransformer)

            features.addAll(additionalFeatures)

            return Project(
                db,
                projectDir.toString(),
                projectJsonModel,
                allCpFiles,
                features,
                projectJsonModel.javaHome,
                projectModulesFiles,
                dependencyFiles
            )
        }
    }
}
