plugins {
    java
    `java-library`
    `maven-publish`
}

repositories {
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(Libs.jacodb_api_jvm)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
