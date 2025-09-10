plugins {
    java
    `java-library`
    `maven-publish`
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
    withType<Jar> {
        manifest {
            attributes(
                mapOf(
                    "Premain-Class" to "org.usvm.jvm.concrete.agent.Agent",
                )
            )
        }
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
