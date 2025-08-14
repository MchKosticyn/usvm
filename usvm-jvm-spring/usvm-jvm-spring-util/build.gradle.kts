plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-jvm:usvm-jvm-api"))
    implementation(project(":usvm-jvm-concrete"))
    implementation(project(":usvm-jvm:usvm-jvm-util"))

    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_approximations)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
