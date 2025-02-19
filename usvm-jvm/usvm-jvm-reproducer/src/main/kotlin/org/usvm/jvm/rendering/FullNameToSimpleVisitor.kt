package org.usvm.jvm.rendering

import com.github.javaparser.ast.expr.Name
import com.github.javaparser.ast.expr.NameExpr
import com.github.javaparser.ast.expr.SimpleName
import com.github.javaparser.ast.type.ClassOrInterfaceType
import com.github.javaparser.ast.visitor.ModifierVisitor
import com.github.javaparser.ast.visitor.Visitable

class FullNameToSimpleVisitor(private val fullNameToSimple: Map<String, String>) : ModifierVisitor<Unit>() {
    override fun visit(n: NameExpr, arg: Unit): Visitable {
        if (fullNameToSimple.containsKey(n.name.identifier))
            n.name = SimpleName(fullNameToSimple[n.name.identifier])
        return super.visit(n, arg)
    }

    override fun visit(n: ClassOrInterfaceType, arg: Unit): Visitable {
        if (fullNameToSimple.containsKey(n.name.identifier))
            n.name = SimpleName(fullNameToSimple[n.name.identifier])
        return super.visit(n, arg)
    }

    override fun visit(n: Name, arg: Unit): Visitable {
        if (fullNameToSimple.containsKey(n.identifier))
            n.identifier = fullNameToSimple[n.identifier]
        return super.visit(n, arg)
    }
}