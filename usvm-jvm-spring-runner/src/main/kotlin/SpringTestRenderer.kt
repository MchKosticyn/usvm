import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.usvm.jvm.rendering.JcTestsRenderer
import org.usvm.jvm.rendering.spring.webMvcTestRenderer.JcSpringMvcTestInfo
import org.usvm.test.api.UTest

class SpringTestRenderer(
    private val cp: JcClasspath
) {
    private val renderer: JcTestsRenderer = JcTestsRenderer()

    fun render(test: UTest, method: JcMethod, isExceptional: Boolean): String {
        val info = JcSpringMvcTestInfo(method, isExceptional)
        val result = renderer.renderTests(cp, listOf(test to info), true)
        return result.entries.single().value
    }
}
