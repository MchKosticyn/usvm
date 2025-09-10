plugins {
    java
    `java-library`
    `maven-publish`
}

repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    compileOnly(Libs.jacodb_api_jvm)
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = JavaVersion.VERSION_1_8.toString()
        targetCompatibility = JavaVersion.VERSION_1_8.toString()
        options.encoding = "UTF-8"
        options.compilerArgs.add("-Xlint:all")
        options.compilerArgs.add("-Xlint:-options")
        options.compilerArgs.add("-Werror")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
