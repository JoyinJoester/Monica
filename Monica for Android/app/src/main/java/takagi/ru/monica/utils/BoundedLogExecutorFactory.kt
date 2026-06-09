package takagi.ru.monica.utils

import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

object BoundedLogExecutorFactory {
    private const val DEFAULT_QUEUE_CAPACITY = 512

    fun createSingleThreadExecutor(
        threadName: String,
        queueCapacity: Int = DEFAULT_QUEUE_CAPACITY
    ): ExecutorService {
        return ThreadPoolExecutor(
            1,
            1,
            0L,
            TimeUnit.MILLISECONDS,
            ArrayBlockingQueue(queueCapacity.coerceAtLeast(1)),
            { runnable -> Thread(runnable, threadName).apply { isDaemon = true } },
            ThreadPoolExecutor.DiscardOldestPolicy()
        )
    }
}
