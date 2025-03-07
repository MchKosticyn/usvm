package org.usvm.jvm.rendering

import com.github.javaparser.ast.NodeList
import com.github.javaparser.ast.stmt.BlockStmt
import com.github.javaparser.ast.stmt.Statement

class JcBlockRenderer(
    importManager: JcImportManager
): JcCodeRenderer<BlockStmt>(importManager) {
    private val statements = NodeList<Statement>()

    override fun renderInternal(): BlockStmt {
        return BlockStmt(statements)
    }
}
