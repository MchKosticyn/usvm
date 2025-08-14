rootProject.name = "usvm"

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.name == "rdgen") {
                useModule("com.jetbrains.rd:rd-gen:${requested.version}")
            }
        }
    }
}

plugins {
    // https://plugins.gradle.org/plugin/com.gradle.develocity
    id("com.gradle.develocity") version "4.0.2"
}

develocity {
    buildScan {
        // Accept the term of use for the build scan plugin:
        termsOfUseUrl.set("https://gradle.com/help/legal-terms-of-use")
        termsOfUseAgree.set("yes")

        // In CI, publish build scans automatically.
        // Locally, publish build scans on-demand, when `--scan` option is provided:
        publishing.onlyIf { System.getenv("CI") != null }
    }
}

include("usvm-core")
include("usvm-jvm")
include("usvm-jvm:usvm-jvm-api")
include("usvm-jvm:usvm-jvm-test-api")
include("usvm-jvm:usvm-jvm-util")
include("usvm-jvm-rendering")
//include("usvm-ts")
include("usvm-util")
include("usvm-jvm-instrumentation")
include("usvm-sample-language")
include("usvm-dataflow")
include("usvm-jvm-dataflow")
//include("usvm-ts-dataflow")

include("usvm-python")
include("usvm-python:cpythonadapter")
findProject(":usvm-python:cpythonadapter")?.name = "cpythonadapter"
include("usvm-python:usvm-python-annotations")
findProject(":usvm-python:usvm-python-annotations")?.name = "usvm-python-annotations"
include("usvm-python:usvm-python-main")
findProject(":usvm-python:usvm-python-main")?.name = "usvm-python-main"
include("usvm-python:usvm-python-runner")
findProject(":usvm-python:usvm-python-runner")?.name = "usvm-python-runner"
include("usvm-python:usvm-python-commons")
findProject(":usvm-python:usvm-python-commons")?.name = "usvm-python-commons"

include("usvm-jvm-concrete")
include("usvm-jvm-concrete:usvm-jvm-concrete-api")
findProject(":usvm-jvm-concrete:usvm-jvm-concrete-api")?.name = "usvm-jvm-concrete-api"
include("usvm-jvm-spring:usvm-jvm-spring-test-api")
include("usvm-jvm-concrete:agent")
findProject(":usvm-jvm-concrete:agent")?.name = "agent"
include("usvm-jvm-spring")
include("usvm-jvm-spring:usvm-jvm-spring-util")
include("usvm-jvm-spring:usvm-jvm-spring-api")
findProject(":usvm-jvm-spring:usvm-jvm-spring-api")?.name = "usvm-jvm-spring-api"
include("usvm-jvm-spring:usvm-jvm-spring-test-api")
findProject(":usvm-jvm-spring:usvm-jvm-spring-test-api")?.name = "usvm-jvm-spring-test-api"
include("usvm-jvm-spring-runner")

// Actually, `includeBuild("../jacodb")` is enough, but there is a bug in IDEA when path is a symlink.
// As a workaround, we convert it to a real absolute path.
// See IDEA bug: https://youtrack.jetbrains.com/issue/IDEA-329756
// val jacodbPath = file("jacodb").takeIf { it.exists() }
//     ?: file("../jacodb").takeIf { it.exists() }
//     ?: error("Local JacoDB directory not found")
// includeBuild(jacodbPath.toPath().toRealPath().toAbsolutePath()) {
//     dependencySubstitution {
//         all {
//             val requested = requested
//             if (requested is ModuleComponentSelector && requested.group == "com.github.UnitTestBot.jacodb") {
//                 val targetProject = ":${requested.module}"
//                 useTarget(project(targetProject))
//                 logger.info("Substituting ${requested.group}:${requested.module} with $targetProject")
//             }
//         }
//     }
// }
