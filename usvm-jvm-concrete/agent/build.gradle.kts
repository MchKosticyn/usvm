plugins {
    java
    `java-library`
    `maven-publish`
}

tasks.withType<Jar> {
    manifest {
        attributes(
            mapOf(
                "Premain-Class" to "org.usvm.jvm.concrete.agent.Agent",
            )
        )
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
