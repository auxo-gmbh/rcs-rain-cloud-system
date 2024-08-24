package master.thesis.raincloudsystem.shared.task.emitter.impl

import master.thesis.raincloudsystem.coordinatr.strategy.Strategy
import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.config.properties.TasksProperties
import master.thesis.raincloudsystem.shared.task.SupportedTask
import master.thesis.raincloudsystem.shared.task.emitter.AbstractTaskTypeEmitter
import master.thesis.raincloudsystem.shared.task.types.impl.TaskType4
import org.springframework.stereotype.Component

@Component
class TaskType4Emitter(
    private val tasksProperties: TasksProperties,
    private val rcsProperties: RCSProperties,
    strategy: Strategy,
    taskType4: TaskType4
) : AbstractTaskTypeEmitter(
    rcsProperties,
    strategy
) {
    override var taskType = taskType4.type
    override var emitterFileLocation = tasksProperties.task4.emitterFileLocation
    override var functionExecutionFileLocation = tasksProperties.task4.functionExecutionFileLocation

    override fun getNewTask(functionTime: Double): SupportedTask {
        val taskType = TaskType4(tasksProperties, rcsProperties)
        taskType.functionTime = functionTime
        return taskType
    }
}
