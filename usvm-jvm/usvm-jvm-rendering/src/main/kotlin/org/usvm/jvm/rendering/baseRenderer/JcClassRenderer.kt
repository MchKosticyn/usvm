package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr

open class JcClassRenderer(
    importManager: JcImportManager,
    identifiersManager: JcIdentifiersManager,
    private val name: SimpleName,
    private val modifiers: NodeList<Modifier>,
    private val annotations: NodeList<AnnotationExpr>,
    private val existingMembers: NodeList<BodyDeclaration<*>>
): JcCodeRenderer<ClassOrInterfaceDeclaration>(importManager, identifiersManager) {

    private val renderingMethods: MutableList<JcMethodRenderer> = mutableListOf()

    constructor(
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        decl: ClassOrInterfaceDeclaration
    ): this(importManager, identifiersManager, decl.name, decl.modifiers, decl.annotations, decl.members)

    constructor(
        importManager: JcImportManager,
        name: String,
    ): this(importManager, JcIdentifiersManager(), SimpleName(name), NodeList(), NodeList(), NodeList())

    constructor(
        name: String,
    ): this(JcImportManager(), JcIdentifiersManager(), SimpleName(name), NodeList(), NodeList(), NodeList())


    protected fun addRenderingMethod(render: JcMethodRenderer) {
        renderingMethods.add(render)
    }

    override fun renderInternal(): ClassOrInterfaceDeclaration {
        val renderedMembers = mutableListOf<BodyDeclaration<*>>()
        for (renderer in renderingMethods) {
            try {
                renderedMembers.add(renderer.render())
            } catch (e: Throwable) {
                println("Renderer failed to render method: ${e.message}")
            }
        }
        val members = NodeList(renderedMembers)
        members.addAll(existingMembers)
        return ClassOrInterfaceDeclaration(
            modifiers,
            annotations,
            false,
            name,
            NodeList(),
            NodeList(),
            NodeList(),
            NodeList(),
            members
        )
    }
}
