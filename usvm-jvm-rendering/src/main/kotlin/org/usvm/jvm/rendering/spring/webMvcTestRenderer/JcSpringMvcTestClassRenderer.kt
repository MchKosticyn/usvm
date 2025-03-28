package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.Name
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestClassRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.test.api.UTest

class JcSpringMvcTestClassRenderer : JcSpringUnitTestClassRenderer {

    private val controller: JcClassType

    constructor(
        controller: JcClassType,
        name: String,
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath
    ) : super(name, importManager, identifiersManager, cp) {
        this.controller = controller
        addAnnotation(webMvcAnnotation())
    }

    constructor(
        controller: JcClassType,
        decl: ClassOrInterfaceDeclaration,
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath
    ) : super(decl, importManager, identifiersManager, cp) {
        this.controller = controller
        addAnnotation(webMvcAnnotation())
    }

    private fun webMvcAnnotation(): AnnotationExpr {
        val webMvcTestClass = renderClass("org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest")
        val ctlClassExpr: ClassExpr = renderClassExpression(controller) as ClassExpr
        val annotation = SingleMemberAnnotationExpr(Name(webMvcTestClass.nameWithScope), ctlClassExpr)

        return annotation
    }


    override fun createTestRenderer(
        test: UTest,
        identifiersManager: JcIdentifiersManager,
        name: SimpleName,
        testAnnotation: AnnotationExpr,
    ): JcTestRenderer {
        return JcSpringMvcTestRenderer(
            test,
            this,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            name,
            testAnnotation
        )
    }
}
