package master.thesis.raincloudsystem.shared.task.emitter.impl

import master.thesis.raincloudsystem.coordinatr.strategy.Strategy
import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.config.properties.TasksProperties
import master.thesis.raincloudsystem.shared.task.SupportedTask
import master.thesis.raincloudsystem.shared.task.emitter.AbstractTaskTypeEmitter
import master.thesis.raincloudsystem.shared.task.types.impl.TaskType5
import org.springframework.stereotype.Component

@Component
class TaskType5Emitter(
    private val tasksProperties: TasksProperties,
    private val rcsProperties: RCSProperties,
    strategy: Strategy,
    taskType5: TaskType5
) : AbstractTaskTypeEmitter(
    rcsProperties,
    strategy
) {
    override var taskType = taskType5.type
    override var emitterFileLocation = tasksProperties.task5.emitterFileLocation
    override var functionExecutionFileLocation = tasksProperties.task5.functionExecutionFileLocation

    override fun getNewTask(functionTime: Double): SupportedTask {
        val taskType = TaskType5(tasksProperties, rcsProperties)
        taskType.functionTime = functionTime
        return taskType
    }
}
