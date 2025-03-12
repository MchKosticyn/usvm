package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.expr.AnnotationExpr

open class JcClassRenderer : JcCodeRenderer<ClassOrInterfaceDeclaration> {

    private val name: SimpleName
    private val modifiers: NodeList<Modifier>
    private val annotations: NodeList<AnnotationExpr>
    private val existingMembers: NodeList<BodyDeclaration<*>>

    private constructor(
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        name: SimpleName,
        modifiers: NodeList<Modifier>,
        annotations: NodeList<AnnotationExpr>,
        existingMembers: NodeList<BodyDeclaration<*>>
    ) : super(importManager, identifiersManager) {
        this.name = name
        this.modifiers = modifiers
        this.annotations = annotations
        this.existingMembers = existingMembers
    }

    private val renderingMethods: MutableList<JcMethodRenderer> = mutableListOf()

    protected constructor(
        importManager: JcImportManager,
        decl: ClassOrInterfaceDeclaration
    ) : this(importManager, JcIdentifiersManager(), decl.name, decl.modifiers, decl.annotations, decl.members)

    constructor(
        decl: ClassOrInterfaceDeclaration
    ): this(JcImportManager(), decl)

    protected constructor(
        importManager: JcImportManager,
        name: String
    ): super(importManager, JcIdentifiersManager()) {
        this.name = identifiersManager.generateIdentifier(name)
        this.modifiers = NodeList()
        this.annotations = NodeList()
        this.existingMembers = NodeList()
    }

    constructor(
        name: String
    ): this(JcImportManager(), name)

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
