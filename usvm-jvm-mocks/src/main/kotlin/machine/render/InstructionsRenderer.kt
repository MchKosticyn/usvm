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
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestClassRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeUtilsRenderer
import org.usvm.test.api.UTest

fun renderConfigInfo(cp: JcClasspath, test: UTest, mockConfigInfo: UTestMockConfigInfo): String? {
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
    val lines = text.split("\n")
    var finalText = ""
    for (i in 1 until lines.size - 2) {
        if (comments[i - 1] != "\n") {
            finalText = finalText + "//" + comments[i - 1] + lines[i] + "\n"
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
    override fun renderInternal(): MethodDeclaration {
        val instructions = mockConfigInfo.instructions
        for (inst in instructions) {
//            println(inst.second)
            body.renderInst(inst.first)
        }
        return super.renderInternal()
    }

    fun renderConfigInfo(): List<String> {
        val instructions = mockConfigInfo.instructions
        val lines = mutableListOf<String>()
        for (inst in instructions) {
            lines.add(inst.second + "\n")
        }
        return lines
    }
}
