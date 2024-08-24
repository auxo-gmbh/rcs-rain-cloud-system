package master.thesis.raincloudsystem.shared.task.emitter.impl

import master.thesis.raincloudsystem.coordinatr.strategy.Strategy
import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.config.properties.TasksProperties
import master.thesis.raincloudsystem.shared.task.SupportedTask
import master.thesis.raincloudsystem.shared.task.emitter.AbstractTaskTypeEmitter
import master.thesis.raincloudsystem.shared.task.types.impl.TaskType2
import org.springframework.stereotype.Component

@Component
class TaskType2Emitter(
    private val tasksProperties: TasksProperties,
    private val rcsProperties: RCSProperties,
    strategy: Strategy,
    taskType2: TaskType2
) : AbstractTaskTypeEmitter(
    rcsProperties,
    strategy
) {
    override var taskType = taskType2.type
    override var emitterFileLocation = tasksProperties.task2.emitterFileLocation
    override var functionExecutionFileLocation = tasksProperties.task2.functionExecutionFileLocation

    override fun getNewTask(functionTime: Double): SupportedTask {
        val taskType = TaskType2(tasksProperties, rcsProperties)
        taskType.functionTime = functionTime
        return taskType
    }
}
