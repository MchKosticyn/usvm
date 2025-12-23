package machine.render

import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.printer.DefaultPrettyPrinter
import machine.instructions.UTestMockConfigInfo
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.testRenderer.JcTestVisitor
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestClassRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeUtilsRenderer
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMethodCall

fun renderConfigInfo(cp: JcClasspath, test: UTest, mockConfigInfo: UTestMockConfigInfo): String {
    val importManager = JcImportManager()
    val identifiersManager = JcIdentifiersManager()
    val strategy = ReflectionUtilsInlineStrategy.NoInline()
    val utilsRenderer = JcUnsafeUtilsRenderer(importManager, strategy)
    val testClassRenderer = JcUnsafeTestClassRenderer(
        "name",
        importManager,
        identifiersManager,
        cp,
        utilsRenderer
    )
    val testRenderer = ConfigInfoRenderer(mockConfigInfo, test, testClassRenderer, importManager, identifiersManager, cp, identifiersManager["test"], listOf(), utilsRenderer)
    val res = testRenderer.render()
    val printer = DefaultPrettyPrinter()
    val text = printer.print(res)
    val comments = testRenderer.renderConfigInfo()
    return modifyText(text, comments)
}

fun modifyText(text: String, comments: List<String>): String {
    val lines = text.split("\n") as MutableList<String>
    lines.removeAt(lines.lastIndex)
    lines.removeAt(lines.lastIndex)
    lines.removeAt(0)
    var finalText = ""
    for (i in 0 until lines.size ) {
        if (comments[i] != "\n") {
            finalText = finalText + "//" + comments[i] + lines[i] + "\n"
        } else {
            finalText = finalText + lines[i] + "\n"
        }
    }
    return finalText
}

class ConfigInfoRenderer(
    private val mockConfigInfo: UTestMockConfigInfo,
    test: UTest,
    classRenderer: JcUnsafeTestClassRenderer,
    importManager: JcImportManager,
    identifiersManager: JcIdentifiersManager,
    cp: JcClasspath,
    name: SimpleName,
    annotations: List<AnnotationExpr>,
    unsafeUtilsRenderer: JcUnsafeUtilsRenderer
) : JcUnsafeTestRenderer(
    test,
    classRenderer,
    importManager,
    identifiersManager,
    cp,
    name,
    annotations,
    unsafeUtilsRenderer
) {
    inner class JcExprUsageVisitor : JcTestVisitor() {
        private fun shouldDeclareVarCheck(expr: UTestExpression): Boolean {
            return !preventVarDeclarationOf(expr) && isVisited(expr) || requireVarDeclarationOf(expr)
        }
        override fun visitExpr(expr: UTestExpression) {
            if (shouldDeclareVarCheck(expr))
                shouldDeclareVar.add(expr)

            super.visitExpr(expr)
        }
        fun visit(instructions: List<UTestInst>) {
            for (inst in instructions) {
                visit(inst)
            }
        }
    }

    init {
        val instructions = mockConfigInfo.instructions.map { it.first }
        JcExprUsageVisitor().visit(instructions)
    }

    private fun getVarsNum(): Set<UTestInst> {
        return shouldDeclareVar
    }

    override fun renderInternal(): MethodDeclaration {
        val instructions = mockConfigInfo.instructions
        for (inst in instructions) {
            body.renderInst(inst.first)
        }
        return super.renderInternal()
    }

    fun renderConfigInfo(): List<String> {
        val instructions = mockConfigInfo.instructions
        val vars = getVarsNum()
        val lines = mutableListOf<String>()
        for (inst in instructions) {
            lines.add(inst.second + "\n")
            if (inst.first is UTestMethodCall && (inst.first as UTestMethodCall).instance in vars) {
                lines.add("\n")
            }
        }
        return lines
    }
}
