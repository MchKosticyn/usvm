package org.usvm.test.api.spring

import org.usvm.test.api.UTestClassExpression

abstract class SpringException(
    val clazz: UTestClassExpression,
    val message: UTString?
)

class UnhandledSpringException(
    val wrapperClass: UTestClassExpression,
    clazz: UTestClassExpression,
    message: UTString?
) : SpringException(clazz, message)

class ResolvedSpringException(
    clazz: UTestClassExpression,
    message: UTString?
) : SpringException(clazz, message)
