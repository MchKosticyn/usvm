package org.usvm.jvm.rendering.testRenderer

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.printer.DefaultPrettyPrinter
import org.usvm.jvm.rendering.testRenderer.testTransformers.JcCallCtorTransformer
import org.usvm.jvm.rendering.testRenderer.testTransformers.JcPrimitiveWrapperTransformer
import org.usvm.jvm.rendering.testRenderer.testTransformers.JcTestTransformer
import org.usvm.jvm.rendering.testTransformers.JcDeadCodeTransformer
import org.usvm.test.api.UTest
import java.io.PrintWriter
import java.net.URI
import java.nio.file.Paths

class JcTestsRenderer {
    private val transformers: List<JcTestTransformer> = listOf(
        JcCallCtorTransformer(),
        JcPrimitiveWrapperTransformer(),
        JcDeadCodeTransformer()
    )

    val testFilePath = Paths.get(URI("usvm-jvm/src/test/kotlin/org/usvm/generated")).toAbsolutePath()
    fun renderTests(tests: List<Pair<UTest, JcTestInfo>>) {
        val testClasses =
            tests.groupBy { (_, info) -> info.method.enclosingClass }

        for ((declType, testsToRender) in testClasses) {
            val testClassName = declType.simpleName + "Tests"
            val testClassRenderer = JcTestClassRenderer(testClassName)

            for ((test, testInfo) in testsToRender) {
                val transformedTest = transformers.fold(test) { currentTest, transformer ->
                    transformer.transform(currentTest)
                }
                val testRenderer = testClassRenderer.addTest(transformedTest, "${testInfo.method}Test")
                testRenderer.render()
            }

            val renderedTestClass = testClassRenderer.render()
            val imports = testClassRenderer.importManager.render()
            val packageDecl = PackageDeclaration(StaticJavaParser.parseName("org.usvm.generated"))
            val cu = CompilationUnit(packageDecl, imports, NodeList(renderedTestClass), null)
            val writer = PrintWriter(testFilePath.toString())
            writer.print(DefaultPrettyPrinter().print(cu))
            writer.close()
        }
    }
}
