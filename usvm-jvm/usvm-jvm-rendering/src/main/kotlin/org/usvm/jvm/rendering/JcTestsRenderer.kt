package org.usvm.jvm.rendering

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.printer.DefaultPrettyPrinter
import org.usvm.jvm.rendering.testRenderer.JcTestInfo
import org.usvm.jvm.rendering.testTransformers.JcCallCtorTransformer
import org.usvm.jvm.rendering.testTransformers.JcPrimitiveWrapperTransformer
import org.usvm.jvm.rendering.testTransformers.JcTestTransformer
import org.usvm.jvm.rendering.testTransformers.JcDeadCodeTransformer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestClassRenderer
import org.usvm.test.api.UTest
import java.io.PrintWriter
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.createDirectories

class JcTestsRenderer {
    private val transformers: List<JcTestTransformer> = listOf(
        JcCallCtorTransformer(),
        JcPrimitiveWrapperTransformer(),
        JcDeadCodeTransformer()
    )

    private val testFilePath = Paths.get("src/test/java/org/usvm/generated").createDirectories()

    // TODO: check rendering in existing file
    fun renderTests(tests: List<Pair<UTest, JcTestInfo>>) {
        println("Test File Path: $testFilePath")

        val testClasses = tests.groupBy { (_, info) -> info.method.enclosingClass }

        for ((declType, testsToRender) in testClasses) {
            val testClassName = declType.simpleName + "Tests"
            val testClassRenderer = JcUnsafeTestClassRenderer(testClassName, "org.usvm.jvm.rendering.ReflectionUtils")

            for ((test, testInfo) in testsToRender) {
                val transformedTest = transformers.fold(test) { currentTest, transformer ->
                    transformer.transform(currentTest)
                }
                testClassRenderer.addTest(transformedTest, "${testInfo.method.name}Test")
            }

            val renderedTestClass = testClassRenderer.render()
            val imports = testClassRenderer.importManager.render()
            val packageDecl = PackageDeclaration(StaticJavaParser.parseName("org.usvm.generated"))
            val cu = CompilationUnit(packageDecl, imports, NodeList(renderedTestClass), null)
            val path = testFilePath.resolve("Tests.java")
            Files.deleteIfExists(path)
            val file = Files.createFile(path).toFile()
            val writer = PrintWriter(file)
            writer.print(DefaultPrettyPrinter().print(cu))
            writer.close()
        }
    }
}
