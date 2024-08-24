package master.thesis.raincloudsystem.shared.task.types

import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.config.properties.TasksProperties
import master.thesis.raincloudsystem.shared.task.SupportedTask
import master.thesis.raincloudsystem.shared.utils.Constants
import mu.KotlinLogging
import org.springframework.stereotype.Component

@Component
abstract class AbstractTaskType(
    private val rcsProperties: RCSProperties,
    private val tasksProperties: TasksProperties
) : SupportedTask {

    private val logger = KotlinLogging.logger {}

    override var functionTime: Double? = null

    override fun execute() {
        val minFunctionTimeMs = functionTime!! * Constants.FACTOR_S_TO_MS
        val functionTimeMs = calculateFunctionTime(minFunctionTimeMs)
        logger.info { "Executing task for $functionTimeMs ms..." }
        Thread.sleep(functionTimeMs)
    }

    private fun calculateFunctionTime(minFunctionTime: Double): Long {
        val resourcesList = listOf(rcsProperties.communication, rcsProperties.computation, rcsProperties.storage)
        val functionTime = minFunctionTime * tasksProperties.functionTimeMultiplyFactor * resourcesList.average()
        return functionTime.toLong()
    }
}
