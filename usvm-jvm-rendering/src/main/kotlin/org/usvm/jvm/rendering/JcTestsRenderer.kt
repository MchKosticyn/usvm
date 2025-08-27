package org.usvm.jvm.rendering

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.printer.DefaultPrettyPrinter
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.testRenderer.JcTestInfo
import org.usvm.jvm.rendering.testTransformers.JcCallCtorTransformer
import org.usvm.jvm.rendering.testTransformers.JcPrimitiveWrapperTransformer
import org.usvm.jvm.rendering.testTransformers.JcTestTransformer
import org.usvm.jvm.rendering.testTransformers.JcDeadCodeTransformer
import org.usvm.jvm.rendering.testTransformers.JcOuterThisTransformer
import org.usvm.test.api.UTest

class JcTestsRenderer {
    private val transformers: List<JcTestTransformer> = listOf(
        JcOuterThisTransformer(),
        JcPrimitiveWrapperTransformer(),
        JcCallCtorTransformer(),
        JcDeadCodeTransformer()
    )

    fun renderTests(
        cp: JcClasspath,
        tests: List<Pair<UTest, JcTestInfo>>,
        reflectionUtilsInlineStrategy: ReflectionUtilsInlineStrategy = ReflectionUtilsInlineStrategy.NoInline,
        isAccessibleFromTestClass: ((JcClassOrInterface) -> Boolean)?
    ): Map<JcTestClassInfo, String> {
        val renderedFiles = mutableMapOf<JcTestClassInfo, String>()
        val testClasses = tests.groupBy { (_, info) -> JcTestClassInfo.from(info) }
        val printer = DefaultPrettyPrinter()

        for ((testClassInfo, testsToRender) in testClasses) {

            val testFile = testClassInfo.testFilePath
            val fileRenderer = when {
                testFile != null -> {
                    JcTestFileRendererFactory.testFileRendererFor(
                        StaticJavaParser.parse(testFile),
                        cp,
                        testClassInfo,
                        reflectionUtilsInlineStrategy,
                        isAccessibleFromTestClass
                    )
                }
                else -> {
                    JcTestFileRendererFactory.testFileRendererFor(
                        testClassInfo.testPackageName,
                        cp,
                        testClassInfo,
                        reflectionUtilsInlineStrategy,
                        isAccessibleFromTestClass
                    )
                }
            }

            val testClassRenderer = fileRenderer.getOrAddClass(testClassInfo.testClassName)

            for ((test, testInfo) in testsToRender) {
                val transformedTest = transformers.fold(test) { currentTest, transformer ->
                    transformer.transform(currentTest)
                }
                testClassRenderer.addTest(transformedTest, testInfo.testNamePrefix)
            }

            val renderedCu = fileRenderer.render()

            renderedFiles[testClassInfo] = printer.print(renderedCu)
        }
        return renderedFiles
    }
}
