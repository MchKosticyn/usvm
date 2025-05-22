package usvmpython.tasks

import gradle.kotlin.dsl.accessors._a9006b9ce7e8f9c575f851ed32d3837b.main
import gradle.kotlin.dsl.accessors._a9006b9ce7e8f9c575f851ed32d3837b.sourceSets
import org.glavo.javah.JavahTask
import org.gradle.api.Project
import usvmpython.getGeneratedHeadersPath
import usvmpython.CPYTHON_ADAPTER_CLASS


fun Project.generateJNIForCPythonAdapterTask() {
    val task = JavahTask()
    task.outputDir = getGeneratedHeadersPath().toPath()
    val classpath = sourceSets.main.get().runtimeClasspath
    classpath.files.forEach {
        task.addClassPath(it.toPath())
    }
    task.addClass(CPYTHON_ADAPTER_CLASS)
    task.run()
}
