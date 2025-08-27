import machine.JcConcreteMemoryClassLoader
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.ext.packageName
import org.usvm.jvm.rendering.JcTestClassInfo
import org.usvm.jvm.rendering.JcTestsRenderer
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.spring.webMvcTestRenderer.JcSpringMvcTestInfo
import org.usvm.jvm.rendering.testRenderer.JcTestInfo
import org.usvm.test.api.UTest

class SpringTestRenderer(
    private val cp: JcClasspath
) {
    private val renderer: JcTestsRenderer = JcTestsRenderer()

    fun render(test: UTest, method: JcMethod, isExceptional: Boolean): String {
        val info = JcSpringMvcTestInfo(method, isExceptional)
        val isAccessibleFromTestClass = { clazz: JcClassOrInterface ->
            val javaClazz = JcConcreteMemoryClassLoader.loadClass(clazz)
            javaClazz.module.isOpen(method.enclosingClass.packageName)
        }
        val result = renderer.renderTests(cp, listOf(test to info), true, ReflectionUtilsInlineStrategy.NestedClass, isAccessibleFromTestClass)
        return result.entries.single().value
    }

    fun render(tests: List<Pair<UTest, JcTestInfo>>): Map<JcTestClassInfo, String> {
        val isAccessibleFromTestClass = { clazz: JcClassOrInterface ->
            val javaClazz = JcConcreteMemoryClassLoader.loadClass(clazz)
            tests.all { (_, info) ->
                javaClazz.module.isOpen(info.method.enclosingClass.packageName)
            }
        }

        return renderer.renderTests(cp, tests, true, ReflectionUtilsInlineStrategy.NestedClass, isAccessibleFromTestClass)
    }
}
