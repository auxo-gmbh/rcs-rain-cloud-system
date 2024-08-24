package master.thesis.raincloudsystem.shared.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "task")
data class TasksProperties(
    var functionTimeMultiplyFactor: Double,
    var timeToLiveFactor: Long,
    var task1: TaskProperty,
    var task2: TaskProperty,
    var task3: TaskProperty,
    var task4: TaskProperty,
    var task5: TaskProperty
) {
    class TaskProperty(
        var emitterFileLocation: String,
        var functionExecutionFileLocation: String
    )
}
