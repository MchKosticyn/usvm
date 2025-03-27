package org.usvm.jvm.rendering

import com.github.javaparser.StaticJavaParser
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
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath

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

    fun renderTests(cp: JcClasspath, tests: List<Pair<UTest, JcTestInfo>>, shouldInlineUsvmUtils: Boolean) {
        System.err.println("Test File Path: $testFilePath")

        val testClasses = tests.groupBy { (_, info) -> info.method.enclosingClass to JcTestClassInfo.from(info) }

        for ((declTypeAndInfoType, testsToRender) in testClasses) {
            val (declType, testClassInfo) = declTypeAndInfoType
            val outputFile = testClassFile(declType)
            var cu = StaticJavaParser.parse(outputFile)

            val fileRenderer = JcTestFileRendererFactory.testFileRendererFor("org.usvm.generated", cu, cp, testClassInfo, shouldInlineUsvmUtils)

            val testClassName = "${declType.simpleName}Tests"
            val testClassRenderer = fileRenderer.getOrAddClass(testClassName)

            for ((test, testInfo) in testsToRender) {
                val transformedTest = transformers.fold(test) { currentTest, transformer ->
                    transformer.transform(currentTest)
                }
                testClassRenderer.addTest(transformedTest, testInfo.namePrefix)
            }

            val cuRender = fileRenderer.render()

            val writer = PrintWriter(outputFile)
            writer.print(DefaultPrettyPrinter().print(cuRender))
            writer.close()
        }
    }
}
