package machine.render

import java.io.File

fun renderTestFile(ifThrows: Boolean, name: String, type: String, pathToFile: String, mockConfigInfo: String) {
    val fileLines = File(pathToFile).readLines().toMutableList()
    fileLines[0] = "package org.usvm.samples.testOutput;"
    var methodUnderTestLine = -1
    if (ifThrows) {
        for (i in 0 until fileLines.size) {
            if (fileLines[i].contains(type) && fileLines[i].contains(name)) { methodUnderTestLine = i }
        }
    }
    val fileText = renderFinalFileText(methodUnderTestLine, fileLines, mockConfigInfo)
    val fileName = pathToFile.substringAfterLast("/")
    val path = pathToFile.substringBeforeLast("/")
    val newPath = "$path/testOutput/$fileName"
    File(newPath).writeText(fileText)
}

fun renderFinalFileText(methodLineNum: Int, fileLines: List<String>, mockConfigInfo: String): String {
    var fileText = ""
    var firstLine = 0
    val configBlocks = mockConfigInfo.split("//") as MutableList<String>
    configBlocks.removeAt(0)
    val data = mutableListOf<Pair<String, Int>>()
    for (block in configBlocks) {
        val lineNumber = getMethodLineNumber(block)
        data.add(Pair(block, lineNumber))
    }
    val sortedBlocks = (data.sortedBy { it.second }).map { it.first }
    for (block in sortedBlocks) {
        val lineNumber = getMethodLineNumber(block)
        for (i in firstLine until lineNumber - 1) {
            if (i == methodLineNum) {
                val before = fileLines[i].substringBefore(")")
                val after = fileLines[i].substringAfter(")")
                fileText += "$before) throws InstantiationException$after\n"
                continue
            }
            fileText += fileLines[i] + "\n"
        }
        firstLine = lineNumber - 1
        val prefix = fileLines[lineNumber - 1].takeWhile { it == ' ' || it == '\t' }
        val blockLines = block.split("\n")
        for (i in 1 until blockLines.size) {
            fileText = fileText + prefix + blockLines[i] + "\n"
        }
    }
    for (i in firstLine until fileLines.size) {
        fileText += fileLines[i] + "\n"
    }
    return fileText
}

fun getMethodLineNumber(str: String): Int {
    val commentLine = str.substringBefore("\n")
    return commentLine.substringAfterLast("(line:").substringBeforeLast(")").toInt()
}
