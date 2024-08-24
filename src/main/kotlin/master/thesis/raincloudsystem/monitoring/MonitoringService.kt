package master.thesis.raincloudsystem.monitoring

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import master.thesis.raincloudsystem.monitoring.message.DeviceEvent
import master.thesis.raincloudsystem.monitoring.message.GossipsDiscoveryEvent
import master.thesis.raincloudsystem.monitoring.message.LinkEvent
import master.thesis.raincloudsystem.monitoring.message.MessagingEvent
import master.thesis.raincloudsystem.monitoring.message.QueueEvent
import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.model.domain.NodeProfile
import mu.KotlinLogging
import org.springframework.amqp.rabbit.core.RabbitTemplate
import org.springframework.stereotype.Service
import javax.annotation.PostConstruct

@Service
class MonitoringService(
    private val localNodeProfile: NodeProfile,
    private val rcsProperties: RCSProperties,
    private val rabbitTemplate: RabbitTemplate
) {

    private val logger = KotlinLogging.logger {}
    private val json = Json { encodeDefaults = true }

    @PostConstruct
    fun publishOwnNode() {
        val taskTypes = localNodeProfile.taskTypes.map { it.type }
        val event = DeviceEvent(sourcePort = localNodeProfile.remotePort, taskTypes = taskTypes)
        handlePublishingMessage(json.encodeToString(event))
    }

    fun publishMessage(linkEvent: LinkEvent) {
        linkEvent.sourcePort = localNodeProfile.remotePort
        handlePublishingMessage(json.encodeToString(linkEvent))
    }

    fun publishMessage(messagingEvent: MessagingEvent) {
        messagingEvent.sourcePort = localNodeProfile.remotePort
        handlePublishingMessage(json.encodeToString(messagingEvent))
    }

    fun publishMessage(queueEvent: QueueEvent) {
        queueEvent.sourcePort = localNodeProfile.remotePort
        handlePublishingMessage(json.encodeToString(queueEvent))
    }

    fun publishMessage(gossipsDiscoveryEvent: GossipsDiscoveryEvent) {
        gossipsDiscoveryEvent.sourcePort = localNodeProfile.remotePort
        handlePublishingMessage(json.encodeToString(gossipsDiscoveryEvent))
    }

    private fun handlePublishingMessage(message: String) {
        if (rcsProperties.monitoringActive) {
            rabbitTemplate.convertAndSend(message)
            logger.info { "Published $message" }
        }
    }
}
