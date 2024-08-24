package master.thesis.raincloudsystem.communicatr.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "sa")
data class SelfActualizationProperties(
    var initialDelayMs: Int,
    var fixedRateMs: Int,
    var blockListMinutes: Long,
    var pendingTasksCleanUpMinutes: Long
)
