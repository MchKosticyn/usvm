package org.usvm.jvm.rendering

import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.stmt.Statement
import org.usvm.test.api.UTest
import org.usvm.test.api.UTestExpression
import org.usvm.test.api.UTestInst

interface JcTestInstCache {
    val renderer: JcTestRendererOld
    fun initialize(test: UTest): UTest
    fun put(expr: UTestExpression): Expression
    fun getOrElse(expr: UTestExpression, block: () -> Expression): Expression
    fun getRequiredDeclarations(inst: UTestInst): List<Statement>
}
