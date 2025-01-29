package org.usvm.machine.state.concreteMemory

import org.usvm.api.util.JcConcreteMemoryClassLoader
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ThreadFactory

private class JcThreadFactory : ThreadFactory {

    private var currentThread: Thread? = null

    override fun newThread(runnable: Runnable): Thread {
        check(currentThread == null)
        val thread = Thread(runnable)
        thread.contextClassLoader = JcConcreteMemoryClassLoader
        thread.isDaemon = true
        currentThread = thread
        return thread
    }

    fun getCurrentThread(): Thread? {
        return currentThread
    }
}

internal class JcConcreteExecutor: ThreadLocalHelper {
    private val threadFactory = JcThreadFactory()
    private val executor = Executors.newSingleThreadExecutor(threadFactory)
    private val threadLocalType by lazy { ThreadLocal::class.java }
    private var lastTask: Future<*>? = null

    private val alreadyInExecutor: Boolean
        get() = Thread.currentThread() === threadFactory.getCurrentThread()

    private val taskIsRunning: Boolean
        get() = lastTask != null && lastTask?.isDone == false

    fun execute(task: Runnable) {
        if (alreadyInExecutor) {
            check(taskIsRunning)
            task.run()
            return
        }

        check(!taskIsRunning)
        val future = executor.submit(task)
        lastTask = future
        future.get()
    }

    override fun checkIsPresent(threadLocal: Any): Boolean {
        check(threadLocal.javaClass.isThreadLocal)
        val isPresentMethod = threadLocalType.declaredMethods.find { it.name == "isPresent" }!!
        isPresentMethod.isAccessible = true
        var value = false
        execute {
            try {
                value = isPresentMethod.invoke(threadLocal) as Boolean
            } catch (e: Throwable) {
                error("unable to check thread local value is present: $e")
            }
        }

        return value
    }

    override fun getThreadLocalValue(threadLocal: Any): Any? {
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

    override fun setThreadLocalValue(threadLocal: Any, value: Any?) {
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
