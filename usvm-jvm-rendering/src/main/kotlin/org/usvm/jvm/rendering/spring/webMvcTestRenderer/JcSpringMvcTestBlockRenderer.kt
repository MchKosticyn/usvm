package org.usvm.jvm.rendering.spring.webMvcTestRenderer

import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.type.ReferenceType
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeImportManager
import org.usvm.jvm.rendering.spring.unitTestRenderer.JcSpringUnitTestBlockRenderer
import org.usvm.test.api.UTestExpression
import java.util.IdentityHashMap

open class JcSpringMvcTestBlockRenderer private constructor(
    importManager: JcUnsafeImportManager,
    identifiersManager: JcIdentifiersManager,
    shouldDeclareVar: Set<UTestExpression>,
    exprCache: IdentityHashMap<UTestExpression, Expression>,
    thrownExceptions: HashSet<ReferenceType>
) : JcSpringUnitTestBlockRenderer(importManager, identifiersManager, shouldDeclareVar, exprCache, thrownExceptions) {

    constructor(
        importManager: JcUnsafeImportManager,
        identifiersManager: JcIdentifiersManager,
        shouldDeclareVar: Set<UTestExpression>
    ) : this(importManager, identifiersManager, shouldDeclareVar, IdentityHashMap(), HashSet())

    override fun newInnerBlock(): JcSpringMvcTestBlockRenderer {
        return JcSpringMvcTestBlockRenderer(
            importManager,
            JcIdentifiersManager(identifiersManager),
            shouldDeclareVar,
            IdentityHashMap(exprCache),
            thrownExceptions
        )
    }
}
