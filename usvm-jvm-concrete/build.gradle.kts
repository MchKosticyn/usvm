plugins {
    id("usvm.kotlin-conventions")
}

dependencies {
    implementation(project(":usvm-jvm"))
    implementation(project(":usvm-core"))

    implementation(project("usvm-jvm-concrete-api"))
    implementation(project("agent"))
    implementation(project(":usvm-jvm:usvm-jvm-test-api"))
    implementation(project(":usvm-jvm:usvm-jvm-util"))
    implementation(project(":usvm-jvm:usvm-jvm-api"))

    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_approximations)

    implementation("it.unimi.dsi:fastutil-core:8.5.13")
}

// TODO: trying to implement tests

val samples by sourceSets.creating {
    java {
        srcDir("src/samples/java")
    }
}

repositories {
    mavenLocal()
}

val approximations by configurations.creating
val approximationsRepo = "org.usvm.approximations.java.stdlib"
val approximationsVersion = "0.0.0"

dependencies {
    testImplementation(Libs.mockk)
    testImplementation(Libs.junit_jupiter_params)
    testImplementation(Libs.logback)

    testImplementation(samples.output)
    testImplementation(project(":usvm-jvm").dependencyProject.project.sourceSets.getByName("test").output)

    // https://mvnrepository.com/artifact/org.burningwave/core
    // Use it to export all modules to all
    testImplementation("org.burningwave:core:12.62.7")

    approximations(approximationsRepo, "approximations", approximationsVersion)
    testImplementation(approximationsRepo, "tests", approximationsVersion)
}


val samplesImplementation: Configuration by configurations.getting

dependencies {
    samplesImplementation("org.projectlombok:lombok:${Versions.Samples.lombok}")
    samplesImplementation("org.slf4j:slf4j-api:${Versions.Samples.slf4j}")
    samplesImplementation("javax.validation:validation-api:${Versions.Samples.javaxValidation}")
    samplesImplementation("com.github.stephenc.findbugs:findbugs-annotations:${Versions.Samples.findBugs}")
    samplesImplementation("org.jetbrains:annotations:${Versions.Samples.jetbrainsAnnotations}")

    // Use usvm-api in samples for makeSymbolic, assume, etc.
    samplesImplementation(project(":usvm-jvm:usvm-jvm-api"))

    testImplementation(project(":usvm-jvm-instrumentation"))
}

val testSamples by configurations.creating
val testSamplesWithApproximations by configurations.creating

dependencies {
    testSamples(samples.output)
    testSamples(project(":usvm-jvm:usvm-jvm-api"))

    testSamplesWithApproximations(samples.output)
    testSamplesWithApproximations(project(":usvm-jvm:usvm-jvm-api"))
    testSamplesWithApproximations(project(":usvm-jvm-concrete:usvm-jvm-concrete-api"))
    testSamplesWithApproximations(approximationsRepo, "tests", approximationsVersion)
}

val usvmApiJarConfiguration by configurations.creating
dependencies {
    usvmApiJarConfiguration(project(":usvm-jvm:usvm-jvm-api"))
}

tasks.withType<Test> {
    val usvmApiJarPath = usvmApiJarConfiguration.resolvedConfiguration.files.single()
    val usvmApproximationJarPath = approximations.resolvedConfiguration.files.single()

    environment("usvm.jvm.api.jar.path", usvmApiJarPath.absolutePath)
    environment("usvm.jvm.approximations.jar.path", usvmApproximationJarPath.absolutePath)

    environment("usvm.jvm.test.samples", testSamples.asPath)
    environment("usvm.jvm.test.samples.approximations", testSamplesWithApproximations.asPath)

    environment(
        "usvm-jvm-instrumentation-jar",
        project(":usvm-jvm-instrumentation")
            .layout
            .buildDirectory
            .file("libs/usvm-jvm-instrumentation-runner.jar")
            .get().asFile.absolutePath
    )
    environment(
        "usvm-jvm-collectors-jar",
        project(":usvm-jvm-instrumentation")
            .layout
            .buildDirectory
            .file("libs/usvm-jvm-instrumentation-collectors.jar")
            .get().asFile.absolutePath
    )
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
