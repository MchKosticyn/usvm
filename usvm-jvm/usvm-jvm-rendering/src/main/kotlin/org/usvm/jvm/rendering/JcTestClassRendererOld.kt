package org.usvm.jvm.rendering

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.SimpleName
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.createFile
import kotlin.io.path.exists
import kotlin.jvm.optionals.getOrNull
import org.usvm.test.api.UTest

abstract class JcTestClassRendererOld(protected val cu: CompilationUnit, protected val testFilePath: Path) {
    companion object {
        const val TEST_SUFFIX = "Test"
        // TODO: used as full path now
        fun loadFileOrCreateFor(testClassPath: Path): JcTestClassRendererOld =
            if (testClassPath.exists()) createRendererFromFile(testClassPath) else initFileAndRenderer(testClassPath)

        private fun createRendererFromFile(path: Path): JcTestClassRendererOld {
            val cu = StaticJavaParser.parse(path)
            val testClass = cu.getClassByName("TestedClassName$TEST_SUFFIX")
            if (testClass.getOrNull() == null) {
                val tmp = ClassOrInterfaceDeclaration().apply {
                    name = SimpleName("TestedClassName$TEST_SUFFIX")
                    isPublic = true
                }
                cu.addType(tmp)
                return JcTestClassRendererImpl(cu, path, tmp)
            }
            return JcTestClassRendererImpl(cu, path, testClass.get())
        }

        private fun initFileAndRenderer(file: Path): JcTestClassRendererOld {
            file.parent.createDirectories()
            file.createFile()
            val cu = CompilationUnit()
            val testClass = ClassOrInterfaceDeclaration()
            cu.setPackageDeclaration("org.usvm.generated")
            testClass.name = SimpleName("TestedClassName$TEST_SUFFIX")
            cu.addType(testClass)
            return JcTestClassRendererImpl(cu, file, testClass)
        }
    }

    abstract fun renderTest(testName: String, test: UTest)
}
