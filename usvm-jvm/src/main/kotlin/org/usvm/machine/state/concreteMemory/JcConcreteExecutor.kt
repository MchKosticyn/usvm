package org.usvm.machine.state.concreteMemory

import org.usvm.api.util.JcConcreteMemoryClassLoader
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory

private class JcThreadFactory : ThreadFactory {

    private var count = 0

    override fun newThread(runnable: Runnable): Thread {
        check(count == 0)
        val thread = Thread(runnable)
        count++
        thread.contextClassLoader = JcConcreteMemoryClassLoader
        thread.isDaemon = true
        return thread
    }
}

internal class JcConcreteExecutor {
    private val executor = Executors.newSingleThreadExecutor(JcThreadFactory())
    private val threadLocalType by lazy { ThreadLocal::class.java }

    fun execute(task: Runnable) {
        executor.submit(task).get()
    }

    fun getThreadLocalValue(threadLocal: Any): Any? {
        check(threadLocal.javaClass.isThreadLocal)
        val getMethod = threadLocalType.getMethod("get")
        var value: Any? = null
        execute {
            try {
                value = getMethod.invoke(threadLocal)
            } catch (e: Throwable) {
                error("unable to get thread local value: $e")
            }
        }

        check(value == null || !value!!.javaClass.isThreadLocal)
        return value
    }

    fun setThreadLocalValue(threadLocal: Any, value: Any?) {
        check(threadLocal.javaClass.isThreadLocal)
        check(value == null || !value.javaClass.isThreadLocal)
        val setMethod = threadLocalType.getMethod("set", Any::class.java)
        execute {
            try {
                setMethod.invoke(threadLocal, value)
            } catch (e: Throwable) {
                error("unable to set thread local value: $e")
            }
        }
    }
}
