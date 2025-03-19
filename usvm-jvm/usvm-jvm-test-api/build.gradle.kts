plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_core)
    implementation(project(":usvm-jvm:usvm-jvm-util"))
    implementation(project(":usvm-jvm:usvm-jvm-api"))
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}