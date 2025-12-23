package machine.render

import com.github.javaparser.printer.DefaultPrettyPrinter
import machine.instructions.UTestMockConfigInfo
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestClassRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeUtilsRenderer
import org.usvm.test.api.UTest

fun renderMockConfigInfo(cp: JcClasspath, test: UTest, mockConfigInfo: UTestMockConfigInfo): String {
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
    return modifyText(text, reorder(comments))
}

fun reorder(lines: List<String>): List<String> {
    val result = mutableListOf<String>()
    val newlines = mutableListOf<String>()

    for (line in lines) {
        if (line == "\n") {
            newlines.add(line)
        } else {
            result.add(line)
            result.addAll(newlines)
            newlines.clear()
        }
    }
    result.addAll(newlines)
    return result
}

fun modifyText(text: String, comments: List<String>): String {
    val lines = text.split("\n") as MutableList<String>
    lines.removeAt(lines.lastIndex)
    lines.removeAt(lines.lastIndex)
    lines.removeAt(0)
    var finalText = ""
    for (i in 0 until lines.size) {
        if (comments[i] != "\n") {
            finalText = finalText + "//" + comments[i] + lines[i] + "\n"
        } else {
            finalText = finalText + lines[i] + "\n"
        }
    }
    return finalText
}
