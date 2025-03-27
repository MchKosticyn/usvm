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
import org.usvm.jvm.rendering.baseRenderer.JcFileRenderer

class JcTestsRenderer {
    private val transformers: List<JcTestTransformer> = listOf(
        JcOuterThisTransformer(),
        JcPrimitiveWrapperTransformer(),
        JcCallCtorTransformer(),
        JcDeadCodeTransformer()
    )

    private val testFilePath = Paths.get("src/test/java/org/usvm/generated").createDirectories()

    private fun testClassFile(declType: JcClassOrInterface): File? {
        val path = testFilePath.resolve("Tests.java")
        return if (!Files.exists(path)) null else path.toFile()
    }

    fun renderTests(cp: JcClasspath, tests: List<Pair<UTest, JcTestInfo>>, shouldInlineUsvmUtils: Boolean) {
        val testClasses = tests.groupBy { (_, info) -> JcTestClassInfo.from(info) }

        for ((testClassInfo, testsToRender) in testClasses) {
            var outputFile = testClassFile(testClassInfo.clazz)

            val fileRenderer = when {
                outputFile != null -> {
                    JcTestFileRendererFactory.testFileRendererFor(
                        StaticJavaParser.parse(outputFile),
                        cp,
                        testClassInfo,
                        shouldInlineUsvmUtils
                    )
                }
                else -> {
                    JcTestFileRendererFactory.testFileRendererFor(
                        JcFileRenderer.defaultRenderedPackageName,
                        cp,
                        testClassInfo,
                        shouldInlineUsvmUtils
                    )
                }
            }

            val testClassName = testClassInfo.testClassName
            val testClassRenderer = fileRenderer.getOrAddClass(testClassName)

            for ((test, testInfo) in testsToRender) {
                val transformedTest = transformers.fold(test) { currentTest, transformer ->
                    transformer.transform(currentTest)
                }
                testClassRenderer.addTest(transformedTest, testInfo.namePrefix)
            }

            val renderedCu = fileRenderer.render()

            if (outputFile == null) {
                outputFile = Files.createFile(testFilePath.resolve("Tests.java")).toFile()
            }

            val writer = PrintWriter(outputFile)
            writer.print(DefaultPrettyPrinter().print(renderedCu))
            writer.close()
        }
    }
}
