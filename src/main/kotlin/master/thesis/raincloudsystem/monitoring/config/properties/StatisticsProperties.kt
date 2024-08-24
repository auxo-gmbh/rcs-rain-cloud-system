package master.thesis.raincloudsystem.monitoring.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "stats")
data class StatisticsProperties(
    var statisticsCron: String,
    var queueCron: String
)
