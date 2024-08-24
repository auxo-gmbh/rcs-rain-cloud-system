package master.thesis.raincloudsystem.communicatr.config

import master.thesis.raincloudsystem.communicatr.config.properties.CommunicatrProperties
import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.model.domain.NodeProfile
import master.thesis.raincloudsystem.shared.model.domain.Resources
import master.thesis.raincloudsystem.shared.model.domain.Task
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile

@Configuration
class NodeProfileConfig(
    private val rcsProperties: RCSProperties,
    private val communicatrProperties: CommunicatrProperties
) {

    private val logger = KotlinLogging.logger {}

    @Bean
    @Profile("!remote")
    fun localNodeProfile(): NodeProfile {
        val (tasks, resources) = constructTasksAndResources()
        val localNodeProfile = NodeProfile(communicatrProperties.localPort, tasks, resources)
        logger.info { "Local node profile is $localNodeProfile with port ${communicatrProperties.localPort}" }
        return localNodeProfile
    }

    @Bean
    @Profile("remote")
    fun remoteNodeProfile(): NodeProfile {
        val (tasks, resources) = constructTasksAndResources()
        val lastIPPart = communicatrProperties.localIP.substringAfterLast(".").toInt()
        val localNodeProfile = NodeProfile(lastIPPart, tasks, resources)
        logger.info { "Local node profile is $localNodeProfile with port ${communicatrProperties.port}" }
        return localNodeProfile
    }

    private fun constructTasksAndResources(): Pair<List<Task>, Resources> {
        val tasks = rcsProperties.supportedTasks.map { Task(it) }
        val resources = Resources(
            rcsProperties.computation,
            rcsProperties.communication,
            rcsProperties.storage
        )
        return Pair(tasks, resources)
    }
}
