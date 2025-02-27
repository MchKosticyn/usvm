plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-jvm"))
    implementation(project(":usvm-core"))

    implementation(project("usvm-jvm-concrete-api"))
    implementation(project(":usvm-jvm:usvm-jvm-test-api"))
    implementation(project(":usvm-jvm:usvm-jvm-util"))
    implementation(project(":usvm-jvm:usvm-jvm-api"))

    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_approximations)

    implementation("it.unimi.dsi:fastutil-core:8.5.13")
}
