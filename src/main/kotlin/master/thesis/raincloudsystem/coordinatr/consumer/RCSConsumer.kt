package master.thesis.raincloudsystem.coordinatr.consumer

import master.thesis.raincloudsystem.shared.task.RCSTask
import master.thesis.raincloudsystem.shared.utils.StartUpMethod
import master.thesis.raincloudsystem.shared.utils.whileIsTrue
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.util.concurrent.LinkedBlockingDeque
import javax.annotation.PreDestroy

@Component
class RCSConsumer(
    private val queue: LinkedBlockingDeque<RCSTask>
) {

    private val logger = KotlinLogging.logger {}
    private var isRunning = true

    @StartUpMethod
    fun run() {
        isRunning.whileIsTrue {
            val task = queue.take()
            task.execute()
        }
    }

    @PreDestroy
    fun shutdown() {
        logger.info { "RCS consumer is shutting down" }
        isRunning = false
    }
}
