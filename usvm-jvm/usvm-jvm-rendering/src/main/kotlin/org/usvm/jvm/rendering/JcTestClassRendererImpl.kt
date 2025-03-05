package org.usvm.jvm.rendering

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.printer.DefaultPrettyPrinter
import java.io.File
import java.io.PrintWriter
import org.usvm.jvm.rendering.visitors.FullNameToSimpleVisitor
import org.usvm.test.api.UTest


class JcTestClassRendererImpl(
    cu: CompilationUnit,
    testClassFile: File,
    private val testClass: ClassOrInterfaceDeclaration
) :
    JcTestClassRenderer(cu, testClassFile) {
    private val importManager = JcTestImportManagerImpl(cu)
    override fun renderTest(testName: String, test: UTest) {
        val testMethod = testClass.addMethod(testName)
        val renderer = JcTestRendererImpl(testClass, testMethod, importManager)
        testMethod.setBody(renderer.render(test))
        cu.accept(FullNameToSimpleVisitor(cu), Unit)
        val writer = PrintWriter(testFile)
        writer.print(DefaultPrettyPrinter().print(cu))
        writer.close()
    }
}