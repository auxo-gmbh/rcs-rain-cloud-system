package master.thesis.raincloudsystem.shared.config

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.AsyncConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.lang.reflect.Method
import java.util.concurrent.Executor

@Configuration
class AsyncConfig : AsyncUncaughtExceptionHandler, AsyncConfigurer {

    override fun getAsyncUncaughtExceptionHandler(): AsyncUncaughtExceptionHandler? {
        return this
    }

    override fun handleUncaughtException(ex: Throwable, method: Method, vararg params: Any?) {
        ex.printStackTrace()
    }

    override fun getAsyncExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 50
        executor.setThreadNamePrefix("rcsExecutor-")
        executor.initialize()
        return executor
    }
}
