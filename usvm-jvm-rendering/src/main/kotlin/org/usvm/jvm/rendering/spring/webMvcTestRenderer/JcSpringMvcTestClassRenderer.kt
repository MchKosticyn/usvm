package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestClassRenderer
import org.usvm.jvm.rendering.spring.JcSpringImportManager
import org.usvm.jvm.rendering.testTransformers.JcSpringMvcTestTransformer
import org.usvm.test.api.UTest

class JcSpringMvcTestClassRenderer : JcSpringUnitTestClassRenderer {

    private val controller: JcClassType

    private lateinit var testClass: JcClassOrInterface

    constructor(
        controller: JcClassType,
        name: String,
        importManager: JcSpringImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath
    ) : super(name, importManager, identifiersManager, cp) {
        this.controller = controller
    }

    constructor(
        controller: JcClassType,
        decl: ClassOrInterfaceDeclaration,
        importManager: JcSpringImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath
    ) : super(decl, importManager, identifiersManager, cp) {
        this.controller = controller
    }

    override fun createTestRenderer(
        test: UTest,
        identifiersManager: JcIdentifiersManager,
        name: SimpleName,
        testAnnotation: AnnotationExpr,
    ): JcTestRenderer {
        val mvcTransformer = JcSpringMvcTestTransformer()
        val transformedTest = mvcTransformer.transform(test)

        if (!this::testClass.isInitialized) {
            testClass = mvcTransformer.testClass
        } else {
            check(testClass == mvcTransformer.testClass) {
                "only one test class expected for class renderer"
            }
        }

        return JcSpringMvcTestRenderer(
            transformedTest,
            this,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            name,
            testAnnotation,
            mvcTransformer.testClass
        )
    }

    override fun renderInternal(): ClassOrInterfaceDeclaration {
        check(this::testClass.isInitialized) {
            "test class expected in class renderer"
        }

        testClass.annotations.forEach { annotation -> addAnnotation(annotation) }

        return super.renderInternal()
    }
}
