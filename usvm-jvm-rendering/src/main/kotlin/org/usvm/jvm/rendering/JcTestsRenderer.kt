package org.usvm.jvm.rendering

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.printer.DefaultPrettyPrinter
import java.io.File
import org.usvm.jvm.rendering.testRenderer.JcTestInfo
import org.usvm.jvm.rendering.testTransformers.JcCallCtorTransformer
import org.usvm.jvm.rendering.testTransformers.JcPrimitiveWrapperTransformer
import org.usvm.jvm.rendering.testTransformers.JcTestTransformer
import org.usvm.jvm.rendering.testTransformers.JcDeadCodeTransformer
import org.usvm.jvm.rendering.testTransformers.JcOuterThisTransformer
import org.usvm.test.api.UTest
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createDirectories
import kotlin.jvm.optionals.getOrNull
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.testRenderer.JcUnitTestInfo

class JcTestsRenderer {
    private val transformers: List<JcTestTransformer> = listOf(
        JcOuterThisTransformer(),
        JcPrimitiveWrapperTransformer(),
        JcCallCtorTransformer(),
        JcDeadCodeTransformer()
    )

    private val testFilePath = Paths.get("src/test/java/org/usvm/generated").createDirectories()

    private fun testClassFile(declType: JcClassOrInterface): File {
        System.err.println("Generating code for ${declType.name}")
        val path = testFilePath.resolve("Tests.java")
        if (!Files.exists(path)) Files.createFile(path)
        val outputFile = path.toFile()
        return outputFile
    }

    fun renderTests(cp: JcClasspath, tests: List<Pair<UTest, JcTestInfo>>) {
        System.err.println("Test File Path: $testFilePath")

        val testClasses = tests.groupBy { (_, info) -> info.method.enclosingClass to info::class }

        for ((declTypeAndInfoClazz, testsToRender) in testClasses) {
            val declType = declTypeAndInfoClazz.first
            val outputFile = testClassFile(declType)
            var cu = StaticJavaParser.parse(outputFile)

            val testClassName = normalizePrefix(declType.simpleName + "Tests")
            val testClass = cu.getClassByName(testClassName).getOrNull() ?: cu.addClass(testClassName).setModifiers(
                NodeList())
            val testClassRenderer = JcTestClassRendererFactory.classRendererFor(testsToRender.first().second, cp, testClass)

            for ((test, testInfo) in testsToRender) {
                val transformedTest = transformers.fold(test) { currentTest, transformer ->
                    transformer.transform(currentTest)
                }
                val throwsSuffix = if (testInfo is JcUnitTestInfo && testInfo.throws) "Throws" else ""
                val testNamePrefix = normalizePrefix("${testInfo.method.name}${throwsSuffix}Test")
                testClassRenderer.addTest(transformedTest, testNamePrefix)
            }

            val renderedTestClass = testClassRenderer.render()
            val imports = testClassRenderer.importManager.render()
            val packageDecl = PackageDeclaration(StaticJavaParser.parseName("org.usvm.generated"))
            cu = cu.setPackageDeclaration(packageDecl).setImports(imports)
            cu.replace(testClass, renderedTestClass)
            val writer = PrintWriter(outputFile)
            writer.print(DefaultPrettyPrinter().print(cu))
            writer.close()
        }
    }

    private fun normalizePrefix(prefix: String): String =
        prefix.replace("<", "").replace(">", "").replace("$", "")
}
