package org.usvm.bench.project

import kotlinx.serialization.Serializable

@Serializable
data class ProjectModuleClassesJsonModel(
    val isTestModule: Boolean,
    val registeredLocation: String,
    val classNames: List<String>
)

@Serializable
data class ProjectJsonModel(
    val sourceRoot: String,
    val javaHome: String?,
    val dependencyFiles: List<String>,
    val classes: List<ProjectModuleClassesJsonModel>
)
