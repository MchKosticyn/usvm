package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.ClassExpr
import com.github.javaparser.ast.expr.Name
import com.github.javaparser.ast.expr.ArrayInitializerExpr
import com.github.javaparser.ast.expr.BooleanLiteralExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.FieldAccessExpr
import com.github.javaparser.ast.expr.MemberValuePair
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.NormalAnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import org.jacodb.api.jvm.JcClassType
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcMethod
import org.usvm.jvm.rendering.testRenderer.JcTestInfo
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.testRenderer.JcTestRenderer
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestClassRenderer
import org.usvm.jvm.rendering.spring.JcSpringImportManager
import org.usvm.jvm.util.allAnnotations
import org.usvm.test.api.UTest

class JcSpringMvcTestClassRenderer : JcSpringUnitTestClassRenderer {

    private val controller: JcClassType

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

    private var shouldExcludeSecurity = false

    override fun createTestRenderer(
        test: UTest,
        testInfo: JcTestInfo,
        identifiersManager: JcIdentifiersManager,
        name: SimpleName,
        testAnnotation: AnnotationExpr,
    ): JcTestRenderer {
        if (isSecured(testInfo.method)) {
            shouldExcludeSecurity = true
        }

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

    override fun renderInternal(): ClassOrInterfaceDeclaration {
        addAnnotation(webMvcAnnotation(shouldExcludeSecurity))
        if (shouldExcludeSecurity) {
            addAnnotation(autoConfigureMockMvc())
        }

        return super.renderInternal()
    }

    private fun autoConfigureMockMvc(): AnnotationExpr {
        val annotationClass = renderClass("org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc")
        val annotationValuePairs = mutableListOf(MemberValuePair("addFilters", BooleanLiteralExpr(false)))
        return NormalAnnotationExpr(Name(annotationClass.nameWithScope), NodeList(annotationValuePairs))
    }

    private fun webMvcAnnotation(excludeSecurity: Boolean): AnnotationExpr {
        val webMvcTestClass = renderClass("org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest")
        val ctlClassExpr: ClassExpr = renderClassExpression(controller) as ClassExpr

        val annotationValuePairs = mutableListOf(MemberValuePair("value", ctlClassExpr))

        if (excludeSecurity) {
            annotationValuePairs.addAll(securityAnnotationValuePairs)
        }

        val annotation = NormalAnnotationExpr(Name(webMvcTestClass.nameWithScope), NodeList(annotationValuePairs))

        return annotation
    }

    private val excludeFilterAnnotationValue: Expression by lazy {
        val filterAnnotationName = Name(renderClass("org.springframework.context.annotation.ComponentScan.Filter", false).nameWithScope)
        val filterTypeName = NameExpr(renderClass("org.springframework.context.annotation.FilterType", false).nameWithScope)
        val webSecurityConfigurer = renderClass("org.springframework.security.config.annotation.web.WebSecurityConfigurer", false)

        val filterAnnotation = NormalAnnotationExpr(
            filterAnnotationName,
            NodeList(
                listOf(
                    MemberValuePair("type", FieldAccessExpr(filterTypeName, "ASSIGNABLE_TYPE")),
                    MemberValuePair("value", ClassExpr(webSecurityConfigurer))
                )
            )
        )

        ArrayInitializerExpr(NodeList(filterAnnotation))
    }

    private val excludeAutoConfigurationAnnotationValue: Expression by lazy {
        ArrayInitializerExpr(
            NodeList(
                listOf(
                    "org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration",
                    "org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration",
                    "org.springframework.boot.autoconfigure.security.oauth2.client.servlet.OAuth2ClientAutoConfiguration",
                    "org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration"
                ).map { className -> ClassExpr(renderClass(className, false)) }
            )
        )
    }

    private val securityAnnotationValuePairs: List<MemberValuePair> by lazy {
        listOf(
            MemberValuePair("excludeFilters", excludeFilterAnnotationValue),
            MemberValuePair("excludeAutoConfiguration", excludeAutoConfigurationAnnotationValue)
        )
    }

    companion object {
        private val securityAnnotationsNames = listOf(
            "org.springframework.security.access.annotation.Secured",
            /*
                TODO: support?
                    "org.springframework.security.access.prepost.PreAuthorize",
                    "org.springframework.security.access.prepost.PostAuthorize",
                    "org.springframework.security.access.prepost.PreFilter",
                    "org.springframework.security.access.prepost.PostFilter",
                    "javax.annotation.security.RolesAllowed"
             */
        )
    }

    private fun isSecured(method: JcMethod): Boolean {
        val methodAnnotations = method.allAnnotations()
        if (methodAnnotations.any { it.name in securityAnnotationsNames }) return true

        val controllerAnnotations = method.enclosingClass.allAnnotations()
        return controllerAnnotations.any { it.name in securityAnnotationsNames }
    }
}
