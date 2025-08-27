package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.jacodb.api.jvm.JcAnnotation
import org.jacodb.api.jvm.JcClassOrInterface
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.jacodb.api.jvm.PredefinedPrimitives
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestClassRenderer
import org.usvm.jvm.rendering.testTransformers.JcSpringMvcTestTransformer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.test.api.UTest

class JcSpringMvcTestClassRenderer : JcSpringUnitTestClassRenderer {

    private val controller: JcClassType

    private lateinit var testClass: JcClassOrInterface

    constructor(
        controller: JcClassType,
        name: String,
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        accessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : super(name, importManager, identifiersManager, cp, accessibleFromTestClass) {
        this.controller = controller
    }

    constructor(
        controller: JcClassType,
        decl: ClassOrInterfaceDeclaration,
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        accessibleFromTestClass: (JcClassOrInterface) -> Boolean
    ) : super(decl, importManager, identifiersManager, cp, accessibleFromTestClass) {
        this.controller = controller
    }

    override fun createTestRenderer(
        test: UTest,
        identifiersManager: JcIdentifiersManager,
        name: SimpleName,
        annotations: List<AnnotationExpr>,
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

        val testMethodAnnotations = testMethodAnnotationsFrom(testClass).map { renderAnnotation(it) }

        return JcSpringMvcTestRenderer(
            transformedTest,
            this,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            name,
            annotations + testMethodAnnotations,
            mvcTransformer.testClass,
            isAccessibleFromTestClass
        )
    }

    override fun renderInternal(): ClassOrInterfaceDeclaration {
        check(this::testClass.isInitialized) {
            "test class expected in class renderer"
        }

        testClass.annotations.forEach { annotation -> addAnnotation(annotation) }

        return super.renderInternal()
    }

    private fun testMethodAnnotationsFrom(stubClass: JcClassOrInterface): List<JcAnnotation> {
        return stubClass.declaredMethods.single { it.isFakeTest }.annotations
    }

    private val JcMethod.isFakeTest: Boolean
        get() = name == "fakeTest" && returnType.typeName == PredefinedPrimitives.Void && parameters.isEmpty()
}
