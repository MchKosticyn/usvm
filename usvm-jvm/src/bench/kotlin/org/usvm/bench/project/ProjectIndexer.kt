package org.usvm.bench.project

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.jacodb.api.jvm.ByteCodeIndexer
import org.jacodb.api.jvm.JCDBContext
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcDatabase
import org.jacodb.api.jvm.JcFeature
import org.jacodb.api.jvm.JcSignal
import org.jacodb.api.jvm.RegisteredLocation
import org.jacodb.approximation.Approximations
import org.jacodb.impl.JcRamErsSettings
import org.jacodb.impl.features.InMemoryHierarchy
import org.jacodb.impl.features.Usages
import org.jacodb.impl.fs.className
import org.jacodb.impl.jacodb
import org.objectweb.asm.tree.ClassNode
import java.io.File
import java.io.FileWriter
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.OnErrorResult
import kotlin.io.path.copyToRecursively
import kotlin.io.path.createDirectory
import kotlin.io.path.deleteIfExists
import kotlin.io.path.name

class ProjectIndexer {
    private inner class ProjectClassIndexerFeature(
        private val projectModulesFiles: Map<File, ProjectResolver.ProjectModuleClasses>,
        private val locationProjectModules: MutableMap<RegisteredLocation, ProjectResolver.ProjectModuleClasses>,
        private val projectClasses: MutableMap<RegisteredLocation, MutableSet<String>>,
        private val methodIds: MutableCollection<String>
    ) : JcFeature<Nothing, Nothing> {
        override fun newIndexer(jcdb: JcDatabase, location: RegisteredLocation): ByteCodeIndexer =
            ProjectClassIndexer(location, projectModulesFiles, locationProjectModules, projectClasses, methodIds)

        override fun onSignal(signal: JcSignal) {
            // todo: ignore?
        }

        override suspend fun query(classpath: JcClasspath, req: Nothing): Sequence<Nothing> {
            error("Unexpected operation")
        }
    }

    private inner class ProjectClassIndexer(
        private val location: RegisteredLocation,
        private val projectModulesFiles: Map<File, ProjectResolver.ProjectModuleClasses>,
        private val locationProjectModules: MutableMap<RegisteredLocation, ProjectResolver.ProjectModuleClasses>,
        private val projectClasses: MutableMap<RegisteredLocation, MutableSet<String>>,
        private val methodIds: MutableCollection<String>
    ) : ByteCodeIndexer {
        private val projectModule: ProjectResolver.ProjectModuleClasses? by lazy {
            location.jcLocation?.jarOrFolder
                ?.let { projectModulesFiles[it] }
                ?.also { module -> locationProjectModules[location] = module }
        }

        override fun flush(context: JCDBContext) {
        }

        override fun index(classNode: ClassNode) {
            if (projectModule == null) return

            val className = classNode.name.className
            val classes = projectClasses.computeIfAbsent(location) { ConcurrentHashMap.newKeySet() }
            classes.add(className)

            classNode.methods.forEach {
                methodIds.add("$className|${it.desc}|${it.name}")
            }
        }
    }

    @OptIn(ExperimentalPathApi::class)
    fun indexProject(project: ProjectResolver.Project, projectInfoDir: Path) {
        val dependencyFiles = project.dependencies.map { it.toFile() }

        val classesDir = projectInfoDir.resolve(Utils.classesDirName)
        classesDir.createDirectory()

        val moduleFiles = mutableMapOf<File, ProjectResolver.ProjectModuleClasses>()
        for (module in project.modules) {
            for (cls in module.projectModuleClasses) {
                val copiedClassesDir = classesDir.resolve(cls.name).createDirectory()
                cls.copyToRecursively(copiedClassesDir, { _, _, _ -> OnErrorResult.TERMINATE },true)
                if (moduleFiles.putIfAbsent(copiedClassesDir.toFile(), module) != null) {
                    //logger.warn("Project class $cls belongs to multiple modules")
                }
            }
        }

        val allCpFiles = mutableListOf<File>()
        allCpFiles.addAll(moduleFiles.keys)
        allCpFiles.addAll(dependencyFiles)

        val locationProjectModules = ConcurrentHashMap<RegisteredLocation, ProjectResolver.ProjectModuleClasses>()
        val projectClasses = ConcurrentHashMap<RegisteredLocation, MutableSet<String>>()
        val methodIds = ConcurrentLinkedDeque<String>()

        runBlocking {
            jacodb {
                when (val toolchain = project.javaToolchain) {
                    is JavaToolchain.ConcreteJavaToolchain -> {
                        useJavaRuntime(File(toolchain.javaHome))
                    }
                    JavaToolchain.DefaultJavaToolchain -> {
                        useProcessJavaRuntime()
                    }
                }

                persistenceImpl(JcRamErsSettings)

                persistent(projectInfoDir.resolve(Utils.persistenceFileName).toString())

                installFeatures(ProjectClassIndexerFeature(moduleFiles, locationProjectModules, projectClasses, methodIds))
                installFeatures(InMemoryHierarchy)
                installFeatures(Usages)
                installFeatures(Approximations)

                loadByteCode(allCpFiles)
            }.awaitBackgroundJobs()
        }

        val projectClassesJsonModels = projectClasses.map { kv ->
            val module = locationProjectModules.getValue(kv.key)
            ProjectModuleClassesJsonModel(module.isTestModule, kv.key.path, kv.value.toList())
        }

        val javaHome =
            when (val javaToolchain = project.javaToolchain) {
                is JavaToolchain.DefaultJavaToolchain -> null
                is JavaToolchain.ConcreteJavaToolchain -> javaToolchain.javaHome
            }

        val projectClassesJsonModel = ProjectJsonModel(
            sourceRoot = project.sourceRoot.toString(),
            javaHome = javaHome,
            dependencyFiles = dependencyFiles.map { it.toString() },
            classes = projectClassesJsonModels
        )

        val projectClassesJsonString = Json.encodeToString(projectClassesJsonModel)

        FileWriter(projectInfoDir.resolve(Utils.projectFileName).toString()).use { it.write(projectClassesJsonString) }

        projectInfoDir.resolve(Utils.persistenceFileName).deleteIfExists()
    }
}
