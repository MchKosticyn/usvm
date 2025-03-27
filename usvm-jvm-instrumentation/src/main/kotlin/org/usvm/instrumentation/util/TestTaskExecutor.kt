package org.usvm.instrumentation.util

import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.TimeUnit

private class TestThreadFactory(private val customClassLoader: ClassLoader?) : ThreadFactory {

    private var currentThread: Thread? = null

    override fun newThread(runnable: Runnable): Thread {
        check(currentThread == null)
        val thread = Thread(runnable)

        if (customClassLoader != null) {
            thread.contextClassLoader = customClassLoader
            thread.isDaemon = true
        }

        currentThread = thread
        return thread
    }
}

class TestTaskExecutor(customClassLoader: ClassLoader? = null) {
    private val threadFactory = TestThreadFactory(customClassLoader)

    private val executor = Executors.newSingleThreadExecutor(threadFactory)

    fun runWithTimeout(timeout: Long, timeUnit: TimeUnit, task: Runnable): Any? {
        val future = executor.submit(task)
        return future.get(timeout, timeUnit)
    }
}
