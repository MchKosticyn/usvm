package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.MarkerAnnotationExpr
import com.github.javaparser.ast.expr.ThisExpr
import com.github.javaparser.ast.type.ReferenceType
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestBlockRenderer
import org.usvm.test.api.UTestExpression
import java.util.IdentityHashMap
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.ext.hasAnnotation
import org.jacodb.api.jvm.ext.superClasses
import org.usvm.test.api.UTestAllocateMemoryCall

open class JcSpringMvcTestBlockRenderer protected constructor(
    override val methodRenderer: JcSpringMvcTestRenderer,
    importManager: JcUnsafeImportManager,
    identifiersManager: JcIdentifiersManager,
    cp: JcClasspath,
    shouldDeclareVar: Set<UTestExpression>,
    exprCache: IdentityHashMap<UTestExpression, Expression>,
    thrownExceptions: HashSet<ReferenceType>
) : JcSpringUnitTestBlockRenderer(methodRenderer, importManager, identifiersManager, cp, shouldDeclareVar, exprCache, thrownExceptions) {

    constructor(
        methodRenderer: JcSpringMvcTestRenderer,
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        shouldDeclareVar: Set<UTestExpression>
    ) : this(methodRenderer, importManager, identifiersManager, cp, shouldDeclareVar, IdentityHashMap(), HashSet())

    override fun newInnerBlock(): JcSpringMvcTestBlockRenderer {
        return JcSpringMvcTestBlockRenderer(
            methodRenderer,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            shouldDeclareVar,
            IdentityHashMap(exprCache),
            thrownExceptions
        )
    }

    override fun renderAllocateMemoryCall(expr: UTestAllocateMemoryCall): Expression {
        val mockPrefixAndAnnotation = getMockNamePrefixAndAnnotation(expr.clazz)

        if (mockPrefixAndAnnotation == null) return super.renderAllocateMemoryCall(expr)

        val (mockPrefix, mockAnnotation) = mockPrefixAndAnnotation

        val mockField = classRenderer.getOrCreateField(
            renderClass(expr.clazz),
            classRenderer.identifiersManager.generateIdentifier(mockPrefix).asString(),
            annotations = NodeList(mockAnnotation)
        )

        return FieldAccessExpr(ThisExpr(), mockField.asString())
    }

    private fun getMockNamePrefixAndAnnotation(clazz: JcClassOrInterface): Pair<String, AnnotationExpr>? =
        when {
            clazz.name == "org.springframework.test.web.servlet.MockMvc" -> {
                "mockMvc" to autowiredAnnotation(clazz.classpath)
            }

            clazz.superClasses.any { sup -> sup.name == "org.springframework.data.repository.Repository" } -> {
                "repositoryMock" to mockBeanAnnotation(clazz.classpath)
            }

            clazz.hasAnnotation("org.springframework.stereotype.Service") -> {
                "serviceMock" to mockBeanAnnotation(clazz.classpath)
            }

            else -> {
                null
            }
        }

    private fun autowiredAnnotation(cp: JcClasspath): AnnotationExpr {
        val autowired = renderClass("org.springframework.beans.factory.annotation.Autowired")
        return MarkerAnnotationExpr(autowired.nameAsString)
    }

    private fun mockBeanAnnotation(cp: JcClasspath): AnnotationExpr {
        val mockBean = renderClass("org.springframework.boot.test.mock.mockito.MockBean")
        return MarkerAnnotationExpr(mockBean.nameAsString)
    }
}
