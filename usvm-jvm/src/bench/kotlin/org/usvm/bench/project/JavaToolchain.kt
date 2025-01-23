package org.usvm.bench.project

sealed interface JavaToolchain {
    object DefaultJavaToolchain : JavaToolchain
    data class ConcreteJavaToolchain(val javaHome: String) : JavaToolchain
}
