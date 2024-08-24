package master.thesis.raincloudsystem.shared.task

import mu.KotlinLogging
import java.time.Instant

data class ReceivedTask(
    var requestId: String,
    var deadline: Instant,
    val supportedTask: SupportedTask,
    val callbackFunction: () -> Unit
) : RCSTask {

    private val logger = KotlinLogging.logger {}

    override fun execute() {
        logger.info { "Executing received task with requestId $requestId and type ${supportedTask.type}" }
        supportedTask.execute()
        logger.info { "Finished executing received task with requestId $requestId and type ${supportedTask.type}" }
        callbackFunction.invoke()
    }
}
