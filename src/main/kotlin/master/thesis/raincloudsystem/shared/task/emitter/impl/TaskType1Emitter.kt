package master.thesis.raincloudsystem.shared.task.emitter.impl

import master.thesis.raincloudsystem.coordinatr.strategy.Strategy
import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.config.properties.TasksProperties
import master.thesis.raincloudsystem.shared.task.SupportedTask
import master.thesis.raincloudsystem.shared.task.emitter.AbstractTaskTypeEmitter
import master.thesis.raincloudsystem.shared.task.types.impl.TaskType1
import org.springframework.stereotype.Component

@Component
class TaskType1Emitter(
    private val tasksProperties: TasksProperties,
    private val rcsProperties: RCSProperties,
    strategy: Strategy,
    taskType1: TaskType1
) : AbstractTaskTypeEmitter(
    rcsProperties,
    strategy
) {
    override var taskType = taskType1.type
    override var emitterFileLocation = tasksProperties.task1.emitterFileLocation
    override var functionExecutionFileLocation = tasksProperties.task1.functionExecutionFileLocation

    override fun getNewTask(functionTime: Double): SupportedTask {
        val taskType = TaskType1(tasksProperties, rcsProperties)
        taskType.functionTime = functionTime
        return taskType
    }
}
