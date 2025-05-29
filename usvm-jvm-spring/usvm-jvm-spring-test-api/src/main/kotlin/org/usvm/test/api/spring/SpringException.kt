package org.usvm.test.api.spring

import org.usvm.test.api.UTestClassExpression

sealed interface SpringException

class UnhandledSpringException(
    val clazz: UTestClassExpression
) : SpringException

class ResolvedSpringException(
    val clazz: UTestClassExpression,
    val message: UTString?
) : SpringException
