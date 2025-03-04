plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_core)
    implementation(project(":usvm-jvm:usvm-jvm-test-api"))
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.26.3")
}