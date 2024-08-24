package master.thesis.raincloudsystem.shared.task.types.impl

import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.config.properties.TasksProperties
import master.thesis.raincloudsystem.shared.task.types.AbstractTaskType
import org.springframework.stereotype.Component

@Component
class TaskType1(
    tasksProperties: TasksProperties,
    rcsProperties: RCSProperties
) : AbstractTaskType(rcsProperties, tasksProperties) {
    override var type: String = "t1"
    override var timeToLive: Long = 0L
}