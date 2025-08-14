plugins {
    antlr
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-jvm"))
    implementation(project(":usvm-core"))
    implementation(project(":usvm-jvm:usvm-jvm-api"))
    implementation(project(":usvm-jvm:usvm-jvm-util"))

    implementation(project(":usvm-jvm-concrete"))
    implementation(project(":usvm-jvm-concrete:agent"))
    implementation(project(":usvm-jvm:usvm-jvm-test-api"))
    implementation(project("usvm-jvm-spring-test-api"))
    implementation(project("usvm-jvm-spring-api"))
    implementation(project("usvm-jvm-spring-util"))

    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_approximations)
}

tasks.getByName("compileTestKotlin").dependsOn("generateTestGrammarSource")
tasks.getByName("compileKotlin").dependsOn("generateGrammarSource")
