package master.thesis.raincloudsystem.shared.config

import master.thesis.raincloudsystem.communicatr.config.properties.CommunicatrProperties
import master.thesis.raincloudsystem.communicatr.config.properties.SelfActualizationProperties
import master.thesis.raincloudsystem.coordinatr.config.properties.ACOProperties
import master.thesis.raincloudsystem.coordinatr.config.properties.GossipsProperties
import master.thesis.raincloudsystem.monitoring.config.properties.StatisticsProperties
import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.config.properties.TasksProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties(
    RCSProperties::class,
    ACOProperties::class,
    GossipsProperties::class,
    CommunicatrProperties::class,
    SelfActualizationProperties::class,
    StatisticsProperties::class,
    TasksProperties::class
)
class SpringConfig
