package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.type.Type

open class JcClassRenderer : JcCodeRenderer<ClassOrInterfaceDeclaration> {

    private val name: SimpleName
    private val modifiers: NodeList<Modifier>
    private val annotations: NodeList<AnnotationExpr>
    private val members: NodeList<BodyDeclaration<*>>

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
        this.members = existingMembers
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
        this.members = NodeList()
    }

    constructor(
        name: String
    ): this(JcImportManager(), name)

    protected fun addRenderingMethod(render: JcMethodRenderer) {
        renderingMethods.add(render)
    }

    fun addField(
        type: Type,
        name: String,
        modifiers: NodeList<Modifier> = NodeList(),
        annotations: NodeList<AnnotationExpr> = NodeList(),
        initializer: Expression? = null
    ): SimpleName {
        val fieldName = identifiersManager.generateIdentifier(name)
        val declarator = VariableDeclarator(type, fieldName, initializer)
        val decl = FieldDeclaration(modifiers, annotations, NodeList(declarator))
        members.add(decl)

        return fieldName
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
        val allMembers = NodeList(renderedMembers)
        allMembers.addAll(members)
        return ClassOrInterfaceDeclaration(
            modifiers,
            annotations,
            false,
            name,
            NodeList(),
            NodeList(),
            NodeList(),
            NodeList(),
            allMembers
        )
    }
}
