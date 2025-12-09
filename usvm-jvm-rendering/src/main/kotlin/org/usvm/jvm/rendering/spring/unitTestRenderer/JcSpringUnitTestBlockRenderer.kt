package org.usvm.jvm.rendering.spring.unitTestRenderer

import com.github.javaparser.ast.expr.Expression
import com.github.javaparser.ast.type.ReferenceType
import java.util.IdentityHashMap
import org.jacodb.api.jvm.JcClasspath
import org.usvm.jvm.rendering.baseRenderer.JcIdentifiersManager
import org.usvm.jvm.rendering.baseRenderer.JcImportManager
import org.usvm.jvm.rendering.spring.JcSpringReflectionUtilsRenderer
import org.usvm.jvm.rendering.unsafeRenderer.JcUnsafeTestBlockRenderer
import org.usvm.test.api.UTestExpression

open class JcSpringUnitTestBlockRenderer protected constructor(
    override val methodRenderer: JcSpringUnitTestRenderer,
    override val importManager: JcImportManager,
    identifiersManager: JcIdentifiersManager,
    cp: JcClasspath,
    shouldDeclareVar: Set<UTestExpression>,
    exprCache: IdentityHashMap<UTestExpression, Expression>,
    thrownExceptions: HashSet<ReferenceType>,
    unsafeUtilsRenderer: JcSpringReflectionUtilsRenderer
) : JcUnsafeTestBlockRenderer(
    methodRenderer,
    importManager,
    identifiersManager,
    cp,
    shouldDeclareVar,
    exprCache,
    thrownExceptions,
    unsafeUtilsRenderer
) {

    constructor(
        methodRenderer: JcSpringUnitTestRenderer,
        importManager: JcImportManager,
        identifiersManager: JcIdentifiersManager,
        cp: JcClasspath,
        shouldDeclareVar: Set<UTestExpression>,
        unsafeUtilsRenderer: JcSpringReflectionUtilsRenderer
    ) : this(
        methodRenderer,
        importManager,
        identifiersManager,
        cp,
        shouldDeclareVar,
        IdentityHashMap(),
        HashSet(),
        unsafeUtilsRenderer
    )

    override fun newInnerBlock(): JcSpringUnitTestBlockRenderer {
        return JcSpringUnitTestBlockRenderer(
            methodRenderer,
            importManager,
            JcIdentifiersManager(identifiersManager),
            cp,
            shouldDeclareVar,
            IdentityHashMap(exprCache),
            thrownExceptions,
            unsafeUtilsRenderer as JcSpringReflectionUtilsRenderer
        )
    }
}
