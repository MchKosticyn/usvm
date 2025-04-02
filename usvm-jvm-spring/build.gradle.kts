plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-jvm"))
    implementation(project(":usvm-core"))
    implementation(project(":usvm-jvm-rendering"))
    implementation(project(":usvm-jvm:usvm-jvm-api"))

    implementation(project(":usvm-jvm-concrete"))
    implementation(project(":usvm-jvm:usvm-jvm-test-api"))
    implementation(project("usvm-jvm-spring-test-api"))

    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_api_jvm)
}
