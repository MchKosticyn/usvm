package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.Name
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.expr.SingleMemberAnnotationExpr
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestClassRenderer
import org.usvm.test.api.UTest

class JcSpringMvcTestClassRenderer : JcSpringUnitTestClassRenderer {

    constructor(
        name: String,
        reflectionUtilsFullName: String,
        cp: JcClasspath
    ) : super(name, reflectionUtilsFullName, cp)

    constructor(
        decl: ClassOrInterfaceDeclaration,
        reflectionUtilsFullName: String,
        cp: JcClasspath
    ) : super(decl, reflectionUtilsFullName, cp)

    constructor(
        controller: JcClassType,
        decl: ClassOrInterfaceDeclaration,
        cp: JcClasspath
    ) : super(decl, cp) {
        this.controller = controller
    }

    private var controller: JcClassType? = null

    init {
        addAnnotation(webMvcAnnotation())
    }

    private fun webMvcAnnotation(): AnnotationExpr {
        val webMvcTestClass = renderClass("org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest")
        val ctlClassExpr: ClassExpr? = controller?.let { renderClassExpression(it) as ClassExpr }

        val annotation = if (ctlClassExpr == null)
            NormalAnnotationExpr(Name(webMvcTestClass.nameWithScope), NodeList())
        else
            SingleMemberAnnotationExpr(Name(webMvcTestClass.nameWithScope), ctlClassExpr)

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
            identifiersManager,
            cp,
            name,
            testAnnotation
        )
    }
}
