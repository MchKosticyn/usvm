package machine.render

import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.test.api.UTest
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.printer.DefaultPrettyPrinter
import machine.instructions.UTestMockConfigInfo
import org.usvm.jvm.rendering.ReflectionUtilsInlineStrategy
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestClassRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeUtilsRenderer

fun renderConfigInfo (cp : JcClasspath, test : UTest, mockConfigInfo: UTestMockConfigInfo): String? {
    val importManager = JcImportManager()
    val identifiersManager = JcIdentifiersManager()
    val strategy = ReflectionUtilsInlineStrategy.NoInline()
    val utilsRenderer = JcUnsafeUtilsRenderer(importManager, strategy)
    val testClassRenderer = JcUnsafeTestClassRenderer(
        "name",
        importManager,
        identifiersManager,
        cp,
        utilsRenderer
    )
    val testRenderer = ConfigInfoRenderer(mockConfigInfo, test, testClassRenderer, importManager, identifiersManager, cp,identifiersManager["test"], listOf(), utilsRenderer)
    val res = testRenderer.render()
    val printer = DefaultPrettyPrinter()
    val res2 = printer.print(res)
    return res2

}

class ConfigInfoRenderer(
    private val mockConfigInfo: UTestMockConfigInfo,
    test: UTest,
    classRenderer: JcUnsafeTestClassRenderer,
    importManager: JcImportManager,
    identifiersManager: JcIdentifiersManager,
    cp: JcClasspath,
    name: SimpleName,
    annotations: List<AnnotationExpr>,
    unsafeUtilsRenderer: JcUnsafeUtilsRenderer
) : JcUnsafeTestRenderer(
    test,
    classRenderer,
    importManager,
    identifiersManager,
    cp,
    name,
    annotations,
    unsafeUtilsRenderer
) {
    override fun renderInternal(): MethodDeclaration {
        val instructions = mockConfigInfo.instructions
        for (inst in instructions)
            body.renderInst(inst.first)
        return super.renderInternal()
    }
}
