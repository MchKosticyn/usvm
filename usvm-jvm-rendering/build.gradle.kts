plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_core)
    implementation(project(":usvm-jvm:usvm-jvm-util"))
    implementation(project(":usvm-jvm:usvm-jvm-test-api"))
    implementation("com.github.javaparser:javaparser-symbol-solver-core:3.26.3")
}

tasks.withType<ProcessResources> {
    val reflectionUtils = project.sourceSets.main.get().java.find { file ->
        file.name == "ReflectionUtils.java"
    }

    from(reflectionUtils)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
