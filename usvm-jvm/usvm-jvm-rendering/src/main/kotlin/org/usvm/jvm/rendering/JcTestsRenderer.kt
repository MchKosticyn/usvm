package org.usvm.jvm.rendering

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.PackageDeclaration
import com.github.javaparser.ast.expr.Name
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.printer.DefaultPrettyPrinter
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcMethod
import org.usvm.jvm.rendering.testTransformers.JcTestTransformer
import org.usvm.test.api.UTest
import java.io.File
import java.io.PrintWriter
import kotlin.io.path.absolutePathString

class JcTestsRenderer {
    val transformers: List<JcTestTransformer> = TODO()
    fun renderTests(tests: List<Pair<UTest, JcTestInfo>>) {
        val testClasses = mutableMapOf<JcClassOrInterface, MutableList<Pair<JcMethod, UTest>>>()
        for ((test, info) in tests) {
            val method = info.method
            val declType = method.enclosingClass
            testClasses.getOrPut(declType) { mutableListOf() }.add(method to test)
        }

        for ((declType, testsToRender) in testClasses) {
            val testClassName = declType.simpleName + "Tests"
            val testClassRenderer = JcTestClassRenderer(testClassName)
            for ((method, test) in testsToRender) {
                val name = SimpleName(method.name + "Test")
                val testRenderer = testClassRenderer.addTest(name)
                testRenderer.initialize(test)
            }
            val renderedTestClass = testClassRenderer.render()
            val imports = testClassRenderer.importManager.render()
            val packageDecl = PackageDeclaration(Name("org.usvm.generated"))
            val cu = CompilationUnit(packageDecl, imports, NodeList(renderedTestClass), null)
            val writer = PrintWriter(File(testFilePath.absolutePathString()))
            writer.print(DefaultPrettyPrinter().print(cu))
            writer.close()
        }
    }
}
