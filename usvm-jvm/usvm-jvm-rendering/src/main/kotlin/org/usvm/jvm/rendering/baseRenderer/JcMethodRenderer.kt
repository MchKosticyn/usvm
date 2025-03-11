package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.MethodDeclaration
import com.github.javaparser.ast.body.Parameter
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.type.Type

open class JcMethodRenderer(
    importManager: JcImportManager,
    protected open val classRenderer: JcClassRenderer,
    private val name: SimpleName,
    private val modifiers: NodeList<Modifier>,
    private val annotations: NodeList<AnnotationExpr>,
    private val parameters: NodeList<Parameter>,
    private val returnType: Type
): JcCodeRenderer<MethodDeclaration>(importManager) {

    protected open val body: JcBlockRenderer = JcBlockRenderer(importManager)

    override fun renderInternal(): MethodDeclaration {
        return MethodDeclaration(
            modifiers,
            annotations,
            NodeList(),
            returnType,
            name,
            parameters,
            thrownExceptions,
            body.render()
        )
    }
}
