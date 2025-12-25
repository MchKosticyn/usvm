package machine.render

import com.github.javaparser.printer.DefaultPrettyPrinter
import machine.getLineNumber
import machine.instructions.UTestMockConfigInfo
import machine.varnamesMap
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestClassRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeUtilsRenderer
import org.usvm.test.api.UTest

/**
 * renderMockConfigInfo() takes UTestMockConfigInfo, creates an instance of ConfigInfoRenderer and handles rendering,
 * returning Java-code of configurations and an indicator whether method under test throws Instantiation Exception.
 */
fun renderMockConfigInfo(cp: JcClasspath, test: UTest, mockConfigInfo: UTestMockConfigInfo): Pair<String, Boolean> {
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
    val isExceptional = testRenderer.ifThrowsInstantiationException()
    return Pair(modifyText(text, reorder(comments)), isExceptional)
}

/**
 * Here we combine mocks configuration code respectively with the comments contains metadata.
 */
fun modifyText(text: String, comments: List<String>): String {
    val lines = text.split("\n") as MutableList<String>
    lines.removeAt(lines.lastIndex)
    lines.removeAt(lines.lastIndex)
    lines.removeAt(0)
    var finalText = ""
    var varname = ""
    for (i in 0 until lines.size) {
        if (comments[i] != "\n") {
            val lineNumber = getLineNumber(comments[i])
            varname = varnamesMap[lineNumber].toString()
            val alteredLine = alter(lines[i], varname)
            finalText = finalText + "//" + comments[i] + alteredLine + "\n"
        } else {
            val alteredLine = alter(lines[i], varname)
            finalText = finalText + alteredLine + "\n"
        }
    }
    return finalText
}

/**
 * This is a helper function for reordering comment lines to achieve a proper text order.
 */
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

/**
 * In rendered code there's a slight problem:
 * Mockito.when(classname.method(args) -- it should it fact be a variable name, not a classname.
 * This function fixes the problem, given the line and the variable name.
 */
fun alter(line: String, varname: String): String {
    if (line.contains("Mockito.when")) {
        val retValue = line.substringAfter(".thenReturn")
        val str = line.substringBefore(").thenReturn").substringAfter("Mockito.when(")
        val method = str.substringAfter(".class")
        val res = "Mockito.when($varname$method).thenReturn$retValue"
        return res
    }
    return line.trim()
}
