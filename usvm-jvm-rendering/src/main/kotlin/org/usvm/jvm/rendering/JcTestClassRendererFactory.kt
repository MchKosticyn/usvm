package org.usvm.jvm.rendering

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestClassRenderer
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestInfo
import org.usvm.jvm.rendering.spring.webMvcTestRenderer.JcSpringMvcTestClassRenderer
import org.usvm.jvm.rendering.spring.webMvcTestRenderer.JcSpringMvcTestInfo
import org.usvm.jvm.rendering.testRenderer.JcTestClassRenderer
import org.usvm.jvm.rendering.testRenderer.JcTestInfo
import org.usvm.jvm.rendering.testRenderer.JcUnitTestInfo
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestClassRenderer

object JcTestClassRendererFactory {
    private val rendererCache = mutableMapOf<JcTestInfo, JcTestClassRenderer>()

    fun classRendererFor(
        testInfo: JcTestInfo,
        cp: JcClasspath,
        testClass: ClassOrInterfaceDeclaration
    ): JcTestClassRenderer {
        return rendererCache.computeIfAbsent(testInfo) { info ->
            when (info) {
                is JcSpringMvcTestInfo -> JcSpringMvcTestClassRenderer(info.controller, testClass, cp)
                is JcSpringUnitTestInfo -> JcSpringUnitTestClassRenderer(testClass, cp)
                is JcUnitTestInfo -> JcUnsafeTestClassRenderer(testClass, cp)
                else -> error("$testInfo not supported")

            }
        }
    }
}