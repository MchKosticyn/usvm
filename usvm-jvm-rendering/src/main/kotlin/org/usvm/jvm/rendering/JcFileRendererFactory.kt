package org.usvm.jvm.rendering

import com.github.javaparser.ast.CompilationUnit
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.toType
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestFileRenderer
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestInfo
import org.usvm.jvm.rendering.spring.webMvcTestRenderer.JcSpringMvcTestFileRenderer
import org.usvm.jvm.rendering.spring.webMvcTestRenderer.JcSpringMvcTestInfo
import org.usvm.jvm.rendering.testRenderer.JcTestFileRenderer
import org.usvm.jvm.rendering.testRenderer.JcTestInfo
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestFileRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestInfo

sealed class JcTestClassInfo(val clazz: JcClassOrInterface) {
    class Base(clazz: JcClassOrInterface) : JcTestClassInfo(clazz)

    class Unsafe(clazz: JcClassOrInterface) : JcTestClassInfo(clazz)

    class SpringUnit(clazz: JcClassOrInterface) : JcTestClassInfo(clazz)

    class SpringMvc(clazz: JcClassOrInterface) : JcTestClassInfo(clazz)

    companion object {
        fun from(testInfo: JcTestInfo) = when (testInfo) {
            is JcSpringMvcTestInfo -> SpringMvc(testInfo.method.enclosingClass)
            is JcSpringUnitTestInfo -> SpringUnit(testInfo.method.enclosingClass)
            is JcUnsafeTestInfo -> Unsafe(testInfo.method.enclosingClass)
            else -> Base(testInfo.method.enclosingClass)
        }
    }

    val testClassName: String get() = "${clazz.simpleName}Tests"

    override fun equals(other: Any?): Boolean {
        return other is JcTestClassInfo && clazz == other.clazz
    }

    override fun hashCode(): Int {
        return clazz.hashCode()
    }
}

object JcTestFileRendererFactory {
    fun testFileRendererFor(
        cu: CompilationUnit,
        cp: JcClasspath,
        testClassInfo: JcTestClassInfo,
        shouldInlineUsvmUtils: Boolean
    ): JcTestFileRenderer {
        return when (testClassInfo) {
            is JcTestClassInfo.SpringMvc -> JcSpringMvcTestFileRenderer(testClassInfo.clazz.toType(), cu, cp)
            is JcTestClassInfo.SpringUnit -> JcSpringUnitTestFileRenderer(cu, cp)
            is JcTestClassInfo.Unsafe -> JcUnsafeTestFileRenderer(cu, cp, shouldInlineUsvmUtils)
            is JcTestClassInfo.Base -> JcTestFileRenderer(cu, cp)
        }
    }

    fun testFileRendererFor(
        packageName: String,
        cp: JcClasspath,
        testClassInfo: JcTestClassInfo,
        shouldInlineUsvmUtils: Boolean
    ): JcTestFileRenderer {
        return when (testClassInfo) {
            is JcTestClassInfo.SpringMvc -> JcSpringMvcTestFileRenderer(testClassInfo.clazz.toType(), packageName, cp)
            is JcTestClassInfo.SpringUnit -> JcSpringUnitTestFileRenderer(packageName, cp)
            is JcTestClassInfo.Unsafe -> JcUnsafeTestFileRenderer(packageName, cp, shouldInlineUsvmUtils)
            is JcTestClassInfo.Base -> JcTestFileRenderer(packageName, cp)
        }
    }
}