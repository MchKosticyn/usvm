rootProject.name = "usvm"

include("usvm-core")
include("usvm-jvm")
include("usvm-jvm:usvm-jvm-api")
include("usvm-jvm:usvm-jvm-test-api")
include("usvm-jvm:usvm-jvm-util")
include("usvm-jvm-rendering")
include("usvm-util")
include("usvm-jvm-instrumentation")
include("usvm-sample-language")
include("usvm-dataflow")
include("usvm-jvm-dataflow")

include("usvm-jvm-concrete")
include("usvm-jvm-spring")
include("usvm-jvm-spring-runner")
include("usvm-jvm-concrete:usvm-jvm-concrete-api")
include("usvm-jvm-spring:usvm-jvm-spring-test-api")

// Actually, `includeBuild("../jacodb")` is enough, but there is a bug in IDEA when path is a symlink.
// As a workaround, we convert it to a real absolute path.
// See IDEA bug: https://youtrack.jetbrains.com/issue/IDEA-329756
// includeBuild(file("../jacodb").toPath().toRealPath().toAbsolutePath())

pluginManagement {
    resolutionStrategy {
        eachPlugin {
            if (requested.id.name == "rdgen") {
                useModule("com.jetbrains.rd:rd-gen:${requested.version}")
            }
        }
    }
}
