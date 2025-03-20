package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.Name
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
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

    init {
        addAnnotation(webMvcAnnotation())
    }

    private fun webMvcAnnotation(): AnnotationExpr {
        val webMvcTestClass = renderClass("org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest")
        // TODO: put classExpr for controller class
        val annotation = NormalAnnotationExpr(Name(webMvcTestClass.nameWithScope), NodeList())
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
