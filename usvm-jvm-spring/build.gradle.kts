plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-jvm"))
    implementation(project(":usvm-core"))

    implementation(project(":usvm-jvm-concrete"))

    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_api_jvm)
}
