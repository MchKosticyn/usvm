package org.usvm.jvm.rendering.baseRenderer

import com.github.javaparser.ast.Modifier
import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.body.BodyDeclaration
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration
import com.github.javaparser.ast.body.FieldDeclaration
import com.github.javaparser.ast.body.VariableDeclarator
import com.github.javaparser.ast.expr.AnnotationExpr
import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.expr.MarkerAnnotationExpr
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.type.Type
import org.jacodb.api.jvm.JcClasspath
import org.jacodb.api.jvm.JcField

open class JcClassRenderer : JcCodeRenderer<ClassOrInterfaceDeclaration> {

    internal val name: SimpleName
    private val modifiers: NodeList<Modifier>
    private val annotations: NodeList<AnnotationExpr>
    private val members: NodeList<BodyDeclaration<*>>

    private val renderingMethods: MutableList<JcMethodRenderer> = mutableListOf()

    constructor(
        decl: ClassOrInterfaceDeclaration,
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath
    ) : super(importManager, identifiersManager.extendedWith(decl), cp)
    {
        this.name = decl.name
        this.modifiers = decl.modifiers
        this.annotations = decl.annotations
        this.members = decl.members
    }

    constructor(
        name: String,
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath
    ): super(importManager, identifiersManager, cp) {
        this.name = identifiersManager.generateIdentifier(name)
        this.modifiers = NodeList()
        this.annotations = NodeList()
        this.members = NodeList()
    }

    protected fun addRenderingMethod(render: JcMethodRenderer) {
        renderingMethods.add(render)
    }

    fun getOrCreateField(
        field: JcField,
        initializer: Expression? = null
    ): SimpleName {
        val modifiers: MutableList<Modifier> = mutableListOf()
        if (field.isPublic)
            modifiers.add(Modifier.publicModifier())
        if (field.isStatic)
            modifiers.add(Modifier.staticModifier())
        if (field.isFinal)
            modifiers.add(Modifier.finalModifier())
        if (field.isPrivate)
            modifiers.add(Modifier.privateModifier())
        if (field.isAbstract)
            modifiers.add(Modifier.abstractModifier())
        if (field.isProtected)
            modifiers.add(Modifier.protectedModifier())
        val annotations = field.annotations.map {
            // TODO: create method 'renderAnnotation'
            MarkerAnnotationExpr(it.name)
        }
        val fieldType = cp.findTypeOrNull(field.type.typeName)
            ?: error("Field type ${field.type.typeName} not found in classpath")
        return getOrCreateField(
            renderType(fieldType),
            field.name,
            NodeList(modifiers),
            NodeList(annotations),
            initializer
        )
    }

    fun getOrCreateField(
        type: Type,
        name: String,
        modifiers: NodeList<Modifier> = NodeList(),
        annotations: NodeList<AnnotationExpr> = NodeList(),
        initializer: Expression? = null
    ): SimpleName {
        val fieldExists = members.any {
            it is FieldDeclaration && it.variables.any { variable -> variable.name.asString() == name }
        }

        if (fieldExists)
            return SimpleName(name)

        return addField(type, name, modifiers, annotations, initializer)
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

    fun addAnnotation(annotation: AnnotationExpr) {
        if (!annotations.contains(annotation)) {
            annotations.add(annotation)
        }
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
