package org.usvm.jvm.rendering

import com.github.javaparser.ast.CompilationUnit
import java.nio.file.Path
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

sealed class JcTestClassInfo(val clazz: JcClassOrInterface, protected val filePath: Path?, protected val className: String?) {

    private val defaultTestClassName: String by lazy { "${clazz.simpleName}Tests" }

    val testClassName: String get() = className ?: defaultTestClassName

    val testFilePath: Path? get() = filePath

    class Base(clazz: JcClassOrInterface, testFilePath: Path?, testClassName: String?) :
        JcTestClassInfo(clazz, testFilePath, testClassName)

    class Unsafe(clazz: JcClassOrInterface, testFilePath: Path?, testClassName: String?) :
        JcTestClassInfo(clazz, testFilePath, testClassName)

    class SpringUnit(clazz: JcClassOrInterface, testFilePath: Path?, testClassName: String?) :
        JcTestClassInfo(clazz, testFilePath, testClassName)

    class SpringMvc(clazz: JcClassOrInterface, testFilePath: Path?, testClassName: String?) :
        JcTestClassInfo(clazz, testFilePath, testClassName)

    companion object {
        fun from(testInfo: JcTestInfo) = when (testInfo) {
            is JcSpringMvcTestInfo -> SpringMvc(testInfo.method.enclosingClass, testInfo.testFilePath, testInfo.testClassName)
            is JcSpringUnitTestInfo -> SpringUnit(testInfo.method.enclosingClass, testInfo.testFilePath, testInfo.testClassName)
            is JcUnsafeTestInfo -> Unsafe(testInfo.method.enclosingClass, testInfo.testFilePath, testInfo.testClassName)
            else -> Base(testInfo.method.enclosingClass, testInfo.testFilePath, testInfo.testClassName)
        }
    }

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