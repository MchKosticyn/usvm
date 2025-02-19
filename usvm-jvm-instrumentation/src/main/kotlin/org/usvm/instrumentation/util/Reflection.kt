package org.usvm.instrumentation.util

import org.usvm.jvm.util.withAccessibility
import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.TimeoutException

fun Method.invokeWithAccessibility(instance: Any?, args: List<Any?>): Any? =
    executeWithTimeout {
        withAccessibility {
            invoke(instance, *args.toTypedArray())
        }
    }

fun Constructor<*>.newInstanceWithAccessibility(args: List<Any?>): Any =
    executeWithTimeout {
        withAccessibility {
            newInstance(*args.toTypedArray())
        }
    } ?: error("Cant instantiate class ${this.declaringClass.name}")

fun executeWithTimeout(body: () -> Any?): Any? {
    var result: Any? = null
    val thread = Thread {
        result = try {
            body()
        } catch (e: Throwable) {
            e
        }
    }
    thread.start()
    thread.join(InstrumentationModuleConstants.methodExecutionTimeout.inWholeMilliseconds)
    var isThreadStopped = false
    while (thread.isAlive) {
        @Suppress("DEPRECATION")
        thread.stop()
        isThreadStopped = true
    }
    when {
        isThreadStopped -> throw TimeoutException()
        result is InvocationTargetException -> throw (result as InvocationTargetException).cause ?: result as Throwable
        else -> return result
    }
}
