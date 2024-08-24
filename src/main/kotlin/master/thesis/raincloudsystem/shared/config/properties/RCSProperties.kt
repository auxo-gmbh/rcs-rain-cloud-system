package master.thesis.raincloudsystem.shared.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "rcs")
data class RCSProperties(
    var computation: Long,
    var communication: Long,
    var storage: Long,
    var queueSize: Int,
    var monitoringActive: Boolean,
    var supportedTasks: List<String>,
    var offloadedTasksCleanUpHours: Long
)
