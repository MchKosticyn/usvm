plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_approximations)
    implementation(project(":usvm-jvm-spring"))
    implementation(project(":usvm-jvm:usvm-jvm-util"))
    implementation(project(":usvm-jvm"))
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
