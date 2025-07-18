import java.io.File
import java.util.jar.Attributes
import java.util.jar.Manifest
import java.util.zip.ZipFile
import kotlin.math.abs


object TestDependenciesManager {
    private const val STARTER_TEST_DEPENDENCIES_PATH = "./test-dependencies/starter-test"
    private const val SECURITY_TEST_DEPENDENCIES_PATH = "./test-dependencies/security-test"

    fun getTestDependencies(classes: List<File>): List<File> {
        val requiredVer = getSpringBootStarterVersion(classes)
        check(requiredVer != null)
        val existingVer = findNearestVer(requiredVer)
        // TODO: #AA Add distinct to avoid duplication
        // TODO: #AA Security
        return existingVer.listFiles()?.toList() ?: listOf()
    }

    private fun findNearestVer(version: String) : File {
        val available = File(STARTER_TEST_DEPENDENCIES_PATH).listFiles()
        check(available != null && available.isNotEmpty())
        return available.minBy { abs(versionToNumber(it.name.split("/").last()) - versionToNumber(version)) }
    }

    private fun versionToNumber(version: String): Int {
        return version.split(".").map { it.toInt() }.fold(0) { acc, i -> acc * 100 + i }
    }

    private fun getSpringBootStarterVersion(classes: List<File>): String? {
        for (file in classes) {
            ZipFile(file).use { zipFile ->
                val entries = zipFile.entries().toList()
                val manifestFile = entries.firstOrNull { it.name == "META-INF/MANIFEST.MF" }
                if (manifestFile == null) return@use
                zipFile.getInputStream(manifestFile).use { stream ->
                    val manifest = Manifest(stream)
                    val name = manifest.mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_TITLE)
                    val version = manifest.mainAttributes.getValue(Attributes.Name.IMPLEMENTATION_VERSION)
                    if (name == "Spring Boot") return version
                }
            }
        }
        return null
    }
}
