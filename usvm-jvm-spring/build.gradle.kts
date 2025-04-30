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

    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_approximations)

    implementation("org.hibernate.orm:hibernate-core:6.3.1.Final")
    antlr(dep("org.antlr", "antlr4", "4.10.1"))
}

tasks.getByName("compileTestKotlin").dependsOn("generateTestGrammarSource")
tasks.getByName("compileKotlin").dependsOn("generateGrammarSource")
