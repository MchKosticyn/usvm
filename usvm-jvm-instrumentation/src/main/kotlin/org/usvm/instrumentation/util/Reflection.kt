package org.usvm.instrumentation.util

import org.usvm.jvm.util.withAccessibility
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

fun Method.invokeWithAccessibility(instance: Any?, args: List<Any?>, executor: TestTaskExecutor): Any? =
    executeWithTimeout(executor) {
        withAccessibility {
            invoke(instance, *args.toTypedArray())
        }
    }

fun Constructor<*>.newInstanceWithAccessibility(args: List<Any?>, executor: TestTaskExecutor): Any =
    executeWithTimeout(executor) {
        withAccessibility {
            newInstance(*args.toTypedArray())
        }
    } ?: error("Cant instantiate class ${this.declaringClass.name}")

private fun unfoldException(e: Throwable): Throwable {
    return when {
        e is ExecutionException && e.cause != null -> unfoldException(e.cause!!)
        e is InvocationTargetException -> e.targetException
        else -> e
    }
}

fun executeWithTimeout(executor: TestTaskExecutor, body: () -> Any?): Any? {
    var result: Any? = null
    var exception: Throwable? = null
    val timeout = InstrumentationModuleConstants.methodExecutionTimeout
    // TODO: unify with executor from concrete memory
    executor.runWithTimeout(timeout.inWholeMilliseconds, TimeUnit.MILLISECONDS) {
        try {
            result = body()
        } catch (e: Throwable) {
            exception = unfoldException(e)
        }
    }
    if (exception != null)
        throw exception!!

    return result
}
