package org.usvm.jvm.rendering.visitors

import com.github.javaparser.ast.stmt.EmptyStmt
import com.github.javaparser.ast.visitor.ModifierVisitor
import com.github.javaparser.ast.visitor.Visitable

class EmptyStmtVisitor: ModifierVisitor<Unit>() {
    override fun visit(n: EmptyStmt, arg: Unit?): Visitable? {
        return null
    }
}