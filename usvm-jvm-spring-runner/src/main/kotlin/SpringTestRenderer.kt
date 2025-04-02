import machine.TestRenderer
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.JcTestsRenderer
import org.usvm.jvm.rendering.testRenderer.JcTestInfo
import org.usvm.test.api.UTest

class SpringTestRenderer(
    private val renderer: JcTestsRenderer,
    private val cp: JcClasspath
) : TestRenderer {
    override fun render(test: UTest, info: JcTestInfo) {
        val result = renderer.renderTests(cp, listOf(test to info), false)
        println(result)
    }
}
