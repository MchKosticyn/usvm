package org.usvm.jvm.rendering.visitors

import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.expr.Name
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.visitor.ModifierVisitor
import com.github.javaparser.ast.visitor.Visitable
import kotlin.jvm.optionals.getOrNull

class FullNameToSimpleVisitor(private val fullNameToSimple: Map<String, String>) : ModifierVisitor<Unit>() {

    constructor(cu: CompilationUnit) :
            this(cu.imports.map { imp -> imp.name.asString() }.associateWith { it.split(".").last() })

    override fun visit(n: NameExpr, arg: Unit): Visitable {
        if (fullNameToSimple.containsKey(n.name.identifier))
            n.name = SimpleName(fullNameToSimple[n.name.identifier])
        return super.visit(n, arg)
    }

    override fun visit(n: ClassOrInterfaceType, arg: Unit): Visitable {
        if (fullNameToSimple.containsKey(n.nameWithScope) || n.scope.getOrNull()?.asString() == "java.lang") {
            n.removeScope()
        }
        return super.visit(n, arg)
    }

    override fun visit(n: Name, arg: Unit): Visitable {
        if (fullNameToSimple.containsKey(n.identifier))
            n.identifier = fullNameToSimple[n.identifier]
        return super.visit(n, arg)
    }
}