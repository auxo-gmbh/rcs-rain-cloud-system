package master.thesis.raincloudsystem.shared.task.emitter.impl

import master.thesis.raincloudsystem.coordinatr.strategy.Strategy
import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.config.properties.TasksProperties
import master.thesis.raincloudsystem.shared.task.SupportedTask
import master.thesis.raincloudsystem.shared.task.emitter.AbstractTaskTypeEmitter
import master.thesis.raincloudsystem.shared.task.types.impl.TaskType3
import org.springframework.stereotype.Component

@Component
class TaskType3Emitter(
    private val tasksProperties: TasksProperties,
    private val rcsProperties: RCSProperties,
    strategy: Strategy,
    taskType3: TaskType3
) : AbstractTaskTypeEmitter(
    rcsProperties,
    strategy
) {
    override var taskType = taskType3.type
    override var emitterFileLocation = tasksProperties.task3.emitterFileLocation
    override var functionExecutionFileLocation = tasksProperties.task3.functionExecutionFileLocation

    override fun getNewTask(functionTime: Double): SupportedTask {
        val taskType = TaskType3(tasksProperties, rcsProperties)
        taskType.functionTime = functionTime
        return taskType
    }
}
