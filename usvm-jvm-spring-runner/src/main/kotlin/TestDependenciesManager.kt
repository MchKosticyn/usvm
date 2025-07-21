import java.io.File
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipFile
import kotlin.math.abs


object TestDependenciesManager {
    private const val STARTER_TEST_DEPENDENCIES_PATH = "./test-dependencies/starter-test"
    private const val SECURITY_TEST_DEPENDENCIES_PATH = "./test-dependencies/security-test"

    fun getTestDependencies(classes: List<File>): List<File> {
        val starterVersion = getSpringBootStarterVersion(classes)
        val securityVersion = getSecurityStarterVersion(classes)
        check(starterVersion != null)
        val existingSpringTestDeps = findNearestVer(
            starterVersion,
            File(STARTER_TEST_DEPENDENCIES_PATH)
        )
        var result = existingSpringTestDeps.listFiles()?.toList() ?: listOf()
        if (securityVersion != null) {
            val existingSecurityTestDeps = findNearestVer(
                securityVersion,
                File(SECURITY_TEST_DEPENDENCIES_PATH)
            )
            result += existingSecurityTestDeps
        }
        return (result + classes).distinctBy { it.name }
    }

    private fun findNearestVer(version: String, available: File) : File {
        check(available.isDirectory)
        val files = available.listFiles()
        check(files != null && files.isNotEmpty())
        return files.minBy { abs(versionToNumber(it.name.split("/").last()) - versionToNumber(version)) }
    }

    private fun versionToNumber(version: String): Int {
        return version.split(".").map { it.toInt() }.fold(0) { acc, i -> acc * 100 + i }
    }

    private fun getSpringBootStarterVersion(classes: List<File>): String? {
        for (file in classes) {
            val manifest = readManifest(file) ?: continue
            val name = manifest.mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE)
            val version = manifest.mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION)
            if (name == "Spring Boot") return version
        }
        return null
    }

    private fun getSecurityStarterVersion(classes: List<File>): String? {
        for (file in classes) {
            val manifest = readManifest(file) ?: continue
            val title = manifest.mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE)
            val version = manifest.mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION)
            if (title == "spring-core") return version
        }
        return null
    }

    private fun readManifest(jar: File): Manifest? {
        return ZipFile(jar).use { zipFile ->
            val entries = zipFile.entries().toList()
            val manifestFile = entries.firstOrNull { it.name == "META-INF/MANIFEST.MF" }
            if (manifestFile == null) return@use null
            return@use zipFile.getInputStream(manifestFile).use { stream ->
                val manifest = Manifest(stream)
                manifest
            }
        }
    }
}
