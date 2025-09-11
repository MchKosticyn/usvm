import java.io.File
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipFile
import kotlin.math.abs

internal class TestDependenciesManager(
    private val projectDeps: List<File>
) {
    private companion object {
        private const val STARTER_TEST_DEPENDENCIES_PATH = "./test-dependencies/starter-test"
        private const val SECURITY_TEST_DEPENDENCIES_PATH = "./test-dependencies/security-test"
        private const val VALIDATION_DEPENDENCIES_PATH = "./test-dependencies/validation"
    }

    val springBootVersion = getSpringBootVersion(projectDeps) ?: error("spring boot version not found")

    val securityVersion = getSecurityVersion(projectDeps)

    val allDependencies: List<File> by lazy {
        val existingStarterTestDeps = findVersion(
            springBootVersion,
            File(STARTER_TEST_DEPENDENCIES_PATH)
        )
        val resultTestDeps = existingStarterTestDeps.toMutableList()
        if (securityVersion != null)
            resultTestDeps += findVersion(
                securityVersion,
                File(SECURITY_TEST_DEPENDENCIES_PATH)
            )
        val persistenceApiPackage = findPackage("jakarta.persistence-api", projectDeps)
        if (persistenceApiPackage != null)
            resultTestDeps += findVersion(
                springBootVersion,
                File(VALIDATION_DEPENDENCIES_PATH)
            )

        clearDuplicates(projectDeps, resultTestDeps)
    }

    private fun getSpringBootVersion(projectDeps: List<File>): String? {
        val springBootPackage = findPackage("spring-boot", projectDeps)
            ?: return null
        val mainAttributes = packageMainAttributes(springBootPackage)
            ?: return null
        val title = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE)
        check(title == "Spring Boot")
        return mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION)
    }

    private fun getSecurityVersion(projectDeps: List<File>): String? {
        val springSecurityPackage = findPackage("spring-security-core", projectDeps)
            ?: return null
        val mainAttributes = packageMainAttributes(springSecurityPackage)
            ?: return null
        val title = mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE)
        check(title == "spring-security-core")
        return mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION)
    }

    private fun findPackage(name: String, projectDeps: List<File>): File? {
        return projectDeps.find {
            nameWithoutVersion(it) == name
        }
    }

    private fun clearDuplicates(projectDeps: List<File>, addedTestDeps: List<File>): List<File> {
        val depsWithNames = hashMapOf<String, File>()
        addedTestDeps.associateByTo(depsWithNames) { nameWithoutVersion(it) }
        projectDeps.associateByTo(depsWithNames) { nameWithoutVersion(it) }
        return depsWithNames.values.toList()
    }

    private fun nameWithoutVersion(file: File): String {
        return file.name.substringBeforeLast('-')
    }

    private fun findVersion(version: String, available: File) : List<File> {
        check(available.isDirectory)
        val files = available.listFiles()
        check(files != null && files.isNotEmpty())
        val source = files.minBy { abs(versionToNumber(it.name.split("/").last()) - versionToNumber(version)) }
        val difference = versionToNumber(version) - versionToNumber(source.name.split("/").last())
        check(abs(difference) < 10) {
            "Test dependencies differ more than allowed"
        }
        return source.listFiles()?.toList() ?: listOf()
    }

    private fun versionToNumber(version: String): Int {
        return version.split(".").mapNotNull { it.toIntOrNull() }.fold(0) { acc, i -> acc * 100 + i }
    }

    private fun packageMainAttributes(file: File): Attributes? {
        return readManifest(file)?.mainAttributes
    }

    private fun readManifest(jar: File): Manifest? {
        return ZipFile(jar).use { zipFile ->
            val entries = zipFile.entries().toList()
            val manifestFile = entries.firstOrNull { it.name == "META-INF/MANIFEST.MF" }
            if (manifestFile == null) return@use null
            return@use zipFile.getInputStream(manifestFile).use { stream -> Manifest(stream) }
        }
    }
}
