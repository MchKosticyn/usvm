plugins {
    id("usvm.kotlin-conventions")
}

repositories {
    mavenLocal()
}

dependencies {
    implementation(project(":usvm-jvm"))
    implementation(project(":usvm-jvm-instrumentation"))
    implementation(project(":usvm-jvm-concrete"))
    implementation(project(":usvm-jvm-spring"))
    implementation(project(":usvm-core"))

    implementation(project(":usvm-jvm-concrete:usvm-jvm-concrete-api"))
    implementation(project(":usvm-jvm:usvm-jvm-test-api"))
    implementation(project(":usvm-jvm:usvm-jvm-util"))
    implementation(project(":usvm-jvm:usvm-jvm-api"))

    implementation(Libs.jacodb_api_jvm)
    implementation(Libs.jacodb_core)
    implementation(Libs.jacodb_approximations)

    implementation(Libs.logback)
}

val usvmApiJarConfiguration by configurations.creating
dependencies {
    usvmApiJarConfiguration(project(":usvm-jvm:usvm-jvm-api"))
}

val usvmConcreteApiJarConfiguration by configurations.creating
dependencies {
    usvmConcreteApiJarConfiguration(project(":usvm-jvm-concrete:usvm-jvm-concrete-api"))
}

val approximations by configurations.creating
val approximationsRepo = "org.usvm.approximations.java.stdlib"
val approximationsVersion = "0.0.0"

dependencies {
    approximations(approximationsRepo, "approximations", approximationsVersion)
}

// TODO: make versions flexible
val springVersion = "3.2.0"

//dependencies {
//    implementation("org.springframework.boot:spring-boot-starter-web:$springVersion")
//    implementation("org.springframework.boot:spring-boot-starter-test:$springVersion")
//    implementation("org.springframework.boot:spring-boot-starter-data-jpa:$springVersion")
//    implementation("org.apache.xmlbeans:xmlbeans:5.2.1")
//    implementation("org.springframework.boot:spring-boot-starter-thymeleaf:$springVersion")
//}

val springTestDeps by configurations.creating

dependencies {
    springTestDeps("org.springframework.boot:spring-boot-starter-test:$springVersion")
}

fun createOrClear(file: File) {
    if (file.exists()) {
        file.listFiles()?.forEach { it.deleteRecursively() }
    } else {
        file.mkdirs()
    }
}

tasks.register<JavaExec>("runWebBench") {
    mainClass.set("bench.WebBenchKt")
    classpath = sourceSets.test.get().runtimeClasspath

    systemProperty("jdk.util.jar.enableMultiRelease", false)

    val absolutePaths = springTestDeps.resolvedConfiguration.files.joinToString(";") { it.absolutePath }
    environment("usvm.jvm.springTestDeps.paths", absolutePaths)

    val currentDir = File(System.getProperty("user.dir"))
    val generatedDir = currentDir.resolve("generated")
    createOrClear(generatedDir)
    environment("generatedDir", generatedDir.absolutePath)
    val lambdaDir = generatedDir.resolve("lambdas")
    createOrClear(lambdaDir)
    environment("lambdasDir", lambdaDir.absolutePath)

    val usvmApiJarPath = usvmApiJarConfiguration.resolvedConfiguration.files.single()
    environment("usvm.jvm.api.jar.path", usvmApiJarPath.absolutePath)

    val usvmApproximationJarPath = approximations.resolvedConfiguration.files.single()
    environment("usvm.jvm.approximations.jar.path", usvmApproximationJarPath.absolutePath)

    val usvmConcreteApiJarPath = usvmConcreteApiJarConfiguration.resolvedConfiguration.files.single()
    environment("usvm.jvm.concrete.api.jar.path", usvmConcreteApiJarPath)

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

    jvmArgs = listOf("-Xmx12g") + mutableListOf<String>().apply {
        add("-Djava.security.manager -Djava.security.policy=webExplorationPolicy.policy")
        add("-Djdk.internal.lambda.dumpProxyClasses=${lambdaDir.absolutePath}")
        openPackage("java.base", "jdk.internal.misc")
        openPackage("java.base", "java.lang")
        openPackage("java.base", "java.lang.reflect")
        openPackage("java.base", "sun.security.provider")
        openPackage("java.base", "jdk.internal.event")
        openPackage("java.base", "jdk.internal.jimage")
        openPackage("java.base", "jdk.internal.jimage.decompressor")
        openPackage("java.base", "jdk.internal.jmod")
        openPackage("java.base", "jdk.internal.jtrfs")
        openPackage("java.base", "jdk.internal.loader")
        openPackage("java.base", "jdk.internal.logger")
        openPackage("java.base", "jdk.internal.math")
        openPackage("java.base", "jdk.internal.misc")
        openPackage("java.base", "jdk.internal.module")
        openPackage("java.base", "jdk.internal.org.objectweb.asm.commons")
        openPackage("java.base", "jdk.internal.org.objectweb.asm.signature")
        openPackage("java.base", "jdk.internal.org.objectweb.asm.tree")
        openPackage("java.base", "jdk.internal.org.objectweb.asm.tree.analysis")
        openPackage("java.base", "jdk.internal.org.objectweb.asm.util")
        openPackage("java.base", "jdk.internal.org.xml.sax")
        openPackage("java.base", "jdk.internal.org.xml.sax.helpers")
        openPackage("java.base", "jdk.internal.perf")
        openPackage("java.base", "jdk.internal.platform")
        openPackage("java.base", "jdk.internal.ref")
        openPackage("java.base", "jdk.internal.reflect")
        openPackage("java.base", "jdk.internal.util")
        openPackage("java.base", "jdk.internal.util.jar")
        openPackage("java.base", "jdk.internal.util.xml")
        openPackage("java.base", "jdk.internal.util.xml.impl")
        openPackage("java.base", "jdk.internal.vm")
        openPackage("java.base", "jdk.internal.vm.annotation")
        openPackage("java.base", "java.util.concurrent.atomic")
        openPackage("java.base", "java.io")
        openPackage("java.base", "java.util.zip")
        openPackage("java.base", "java.util.concurrent")
        openPackage("java.base", "sun.security.util")
        openPackage("java.base", "java.lang.invoke")
        openPackage("java.base", "java.lang.ref")
        openPackage("java.base", "java.lang.constant")
        openPackage("java.base", "java.util")
        openPackage("java.base", "java.util.concurrent.locks")
        openPackage("java.management", "javax.management")
        openPackage("java.base", "java.nio.charset")
        openPackage("java.base", "java.util.regex")
        openPackage("java.base", "java.net")
        openPackage("java.base", "sun.util.locale")
        openPackage("java.base", "java.util.stream")
        openPackage("java.base", "java.security")
        openPackage("java.base", "java.time")
        openPackage("java.base", "jdk.internal.access")
        openPackage("java.base", "sun.reflect.annotation")
        openPackage("java.base", "sun.reflect.generics.reflectiveObjects")
        openPackage("java.base", "sun.reflect.generics.factory")
        openPackage("java.base", "sun.reflect.generics.tree")
        openPackage("java.base", "sun.reflect.generics.scope")
        openPackage("java.base", "sun.invoke.util")
        openPackage("java.base", "sun.nio.cs")
        openPackage("java.base", "sun.nio.fs")
        openPackage("java.base", "java.nio")
        openPackage("java.logging", "java.util.logging")
        openPackage("java.base", "java.time.format")
        openPackage("java.base", "java.time.zone")
        openPackage("java.base", "java.time.temporal")
        openPackage("java.base", "java.text")
        openPackage("java.base", "sun.util.calendar")
        openPackage("java.base", "sun.net.www.protocol.jar")
        openPackage("java.base", "java.util.jar")
        openPackage("java.base", "java.nio.file.attribute")
        openPackage("java.base", "java.util.function")
        openPackage("java.desktop", "java.beans")
        openPackage("java.xml", "com.sun.org.apache.xerces.internal.impl.xs")
        openPackage("java.base", "java.math")
        openPackage("java.base", "java.nio.file")
        openPackage("java.base", "java.nio.channels")
        openPackage("java.base", "javax.net.ssl")
        openPackage("java.base", "java.lang.annotation")
        openPackage("java.base", "java.lang.runtime")
        openPackage("java.base", "javax.crypto")
        openPackage("jdk.zipfs", "jdk.nio.zipfs")
        openPackage("java.base", "java.nio.file.spi")
        openPackage("java.base", "jdk.internal.jrtfs")
        openPackage("java.instrument", "sun.instrument")
        openPackage("java.xml", "com.sun.xml.internal.stream")
        openPackage("java.xml", "com.sun.org.apache.xerces.internal.impl")
        openPackage("java.xml", "com.sun.org.apache.xerces.internal.utils")
        openPackage("java.sql", "java.sql")
        openPackage("java.base", "sun.nio.ch")
        openPackage("java.base", "sun.net.util")
        exportPackage("java.base", "jdk.internal.access.foreign")
        exportPackage("java.base", "sun.security.action")
        exportPackage("java.base", "sun.util.locale")
        exportPackage("java.base", "jdk.internal.misc")
        exportPackage("java.base", "jdk.internal.reflect")
        exportPackage("java.base", "sun.nio.cs")
        exportPackage("java.xml", "com.sun.org.apache.xerces.internal.impl.xs.util")
        exportPackage("java.base", "jdk.internal.loader")
        add("--illegal-access=warn")
        add("-XX:+UseParallelGC")
        addModule("jdk.incubator.foreign")
    }
}

fun MutableList<String>.openPackage(module: String, pakage: String) {
    add("--add-opens")
    add("$module/$pakage=ALL-UNNAMED")
}

fun MutableList<String>.exportPackage(module: String, pakage: String) {
    add("--add-exports")
    add("$module/$pakage=ALL-UNNAMED")
}

fun MutableList<String>.addModule(module: String) {
    add("--add-modules")
    add(module)
}

fun JavaExec.addEnvIfExists(envName: String, path: String) {
    val file = File(path)
    if (!file.exists()) {
        println("Not found $envName at $path")
        return
    }

    environment(envName, file.absolutePath)
}
