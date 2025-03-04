package org.usvm.jvm.rendering

import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.printer.DefaultPrettyPrinter
import java.io.File
import javassist.compiler.ast.MethodDecl
import kotlin.jvm.optionals.getOrElse
import kotlin.jvm.optionals.getOrNull
import org.usvm.jvm.rendering.visitors.FullNameToSimpleVisitor
import org.usvm.test.api.UTest

abstract class JcTestClassRenderer(protected val cu: CompilationUnit, protected val testFile: File) {
    companion object {
        const val TEST_SUFFIX = "Test"
        // TODO: used as full path now
        fun loadFileOrCreateFor(testClassName: String): JcTestClassRenderer = File(testClassName).let { file ->
            if (file.exists()) createRendererFromFile(file) else initFileAndRenderer(file)
        }

        private fun createRendererFromFile(file: File): JcTestClassRenderer {
            val cu = StaticJavaParser.parse(file)
            val testClass = cu.getClassByName("TestedClassName$TEST_SUFFIX")
            if (testClass.getOrNull() == null) {
                val tmp = ClassOrInterfaceDeclaration().apply {
                    name = SimpleName("TestedClassName$TEST_SUFFIX")
                    isPublic = true
                }
                cu.addType(tmp)
                return JcTestClassRendererImpl(cu, file, tmp)
            }
            return JcTestClassRendererImpl(cu, file, testClass.get())
        }

        private fun initFileAndRenderer(file: File): JcTestClassRenderer {
            file.createNewFile()
            val cu = CompilationUnit()
            val testClass = ClassOrInterfaceDeclaration()
            cu.setPackageDeclaration("org.usvm.generated")
            testClass.name = SimpleName("TestedClassName$TEST_SUFFIX")
            testClass.isPublic = true
            cu.addType(testClass)
            return JcTestClassRendererImpl(cu, file, testClass)
        }
    }

    abstract fun renderTest(testName: String, test: UTest)
}