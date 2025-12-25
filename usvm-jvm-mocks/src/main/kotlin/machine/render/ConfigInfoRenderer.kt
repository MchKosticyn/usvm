package machine.render

import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import machine.instructions.UTestMockConfigInfo
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.testRenderer.JcTestVisitor
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestClassRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeUtilsRenderer
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestAllocateMemoryCall
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst
import org.usvm.test.api.UTestMockInst

/**
 * ConfigInfoRenderer inherits JcUnsafeTestRenderer and handles rendering Mockito.when(..).thenReturn(..) Java code.
 * It uses JcExprUsageVisitor for the purpose of creating a variable if some value is repeatedly used in the configuration.
 */
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
    inner class JcExprUsageVisitor : JcTestVisitor() {
        private fun shouldDeclareVarCheck(expr: UTestExpression): Boolean {
            return !preventVarDeclarationOf(expr) && isVisited(expr) || requireVarDeclarationOf(expr)
        }
        override fun visitExpr(expr: UTestExpression) {
            if (shouldDeclareVarCheck(expr)) {
                shouldDeclareVar.add(expr)
            }

            super.visitExpr(expr)
        }
        fun visit(instructions: List<UTestInst>) {
            for (inst in instructions) {
                visit(inst)
            }
        }
    }

    init {
        val instructions = mockConfigInfo.instructions.map { it.first }
        JcExprUsageVisitor().visit(instructions)
    }

    private fun getVarsNum(): Set<UTestInst> {
        return shouldDeclareVar
    }

    /**
     * Renders every instruction (every mock configuration) from UTestMockInst.
     */
    override fun renderInternal(): MethodDeclaration {
        val instructions = mockConfigInfo.instructions
        for (inst in instructions) {
            body.renderInst(inst.first)
        }
        return super.renderInternal()
    }

    /**
     * Detects if method under test throws Exception.
     */
    fun ifThrowsInstantiationException(): Boolean {
        for (inst in mockConfigInfo.instructions) {
            if (inst.first is UTestMockInst && (inst.first as UTestMockInst).instance is UTestAllocateMemoryCall) {
                return true
            }
        }
        return false
    }

    /**
     * Renders comments for configuration with metadata.
     */
    fun renderConfigInfo(): List<String> {
        val instructions = mockConfigInfo.instructions
        val vars = getVarsNum()
        val lines = mutableListOf<String>()
        for (inst in instructions) {
            if (inst.first is UTestMockInst && (inst.first as UTestMockInst).instance in vars) {
                lines.add("\n")
            }
            lines.add(inst.second + "\n")
        }
        return lines
    }
}
