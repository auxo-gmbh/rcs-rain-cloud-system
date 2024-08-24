package master.thesis.raincloudsystem.shared.storage

import master.thesis.raincloudsystem.communicatr.connections.links.CommunicationLink
import master.thesis.raincloudsystem.communicatr.model.message.NodeDiscoveryMessage
import master.thesis.raincloudsystem.communicatr.service.AvoidListService
import master.thesis.raincloudsystem.communicatr.service.BlockListService
import master.thesis.raincloudsystem.coordinatr.strategy.impl.ACOAlgorithm
import master.thesis.raincloudsystem.monitoring.MonitoringService
import master.thesis.raincloudsystem.monitoring.enums.EventType
import master.thesis.raincloudsystem.monitoring.message.LinkEvent
import master.thesis.raincloudsystem.shared.exception.NoCommunicationLinkException
import master.thesis.raincloudsystem.shared.model.domain.Entity
import master.thesis.raincloudsystem.shared.model.domain.NodeProfile
import master.thesis.raincloudsystem.shared.model.domain.Resources
import master.thesis.raincloudsystem.shared.model.domain.Task
import master.thesis.raincloudsystem.shared.utils.RemotePort
import master.thesis.raincloudsystem.shared.utils.ifPresentOrElseThrow
import mu.KotlinLogging
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import javax.annotation.PreDestroy

@Component
class Database(
    private val acoAlgorithm: ACOAlgorithm,
    private val gossipsQuality: ConcurrentHashMap<RemotePort, Int>,
    private val monitoringService: MonitoringService,
    private val environment: Environment,
    private val avoidListService: AvoidListService,
    private val blockListService: BlockListService
) {

    private val logger = KotlinLogging.logger {}
    private val database = ConcurrentHashMap<Int, Entity>()

    fun save(message: NodeDiscoveryMessage, communicationLink: CommunicationLink) {
        val remotePort = message.remotePort
        val remoteNodeProfile = constructNodeProfile(message)
        val entity = Entity(remoteNodeProfile, communicationLink)

        if ("ACO" in environment.activeProfiles) {
            acoAlgorithm.initializePheromonesOfConnectedEdges(remoteNodeProfile)
        }
        if ("Gossips" in environment.activeProfiles) {
            gossipsQuality[remotePort] = 0
        }

        database[remotePort] = entity

        logger.info { "Saved $entity with port $remotePort" }
        monitoringService.publishMessage(
            LinkEvent(
                eventType = EventType.ADD_LINK_EVENT,
                targetPort = remotePort,
                openedConnectionAt = System.currentTimeMillis()
            )
        )
    }

    fun deleteByCommunicationLink(communicationLink: CommunicationLink) {
        database.entries.forEach {
            if (it.value.communicationLink == communicationLink) {
                val remotePort = it.key
                if ("ACO" in environment.activeProfiles) {
                    acoAlgorithm.deleteEdgeInfo(remotePort)
                }
                if ("Gossips" in environment.activeProfiles) {
                    gossipsQuality.remove(remotePort)
                }
                val fromSelfActualization = blockListService.isPartOfBlocklist(remotePort)
                if (avoidListService.isPartOfAvoidList(remotePort)) {
                    avoidListService.switchFromAvoidToBlockList(remotePort)
                }
                database.remove(remotePort)
                publishArchiveEvent(remotePort, fromSelfActualization)
                logger.info { "Removed node profile with port $remotePort" }
            }
        }
    }

    fun isAlreadyConnectedTo(remotePort: Int) = database.containsKey(remotePort)

    fun getAllEntities(filter: (entity: Entity) -> Boolean = { true }): List<Entity> {
        return database.values.filter { filter(it) }.map { it }
    }

    fun getNumberOfCommunicationLinks(): Int {
        return database.size
    }

    fun getCommunicationLinkByPort(port: Int): CommunicationLink {
        return database[port].ifPresentOrElseThrow(
            { it.communicationLink },
            NoCommunicationLinkException("Cannot find communication link with port $port")
        )
    }

    fun getEntityByPort(port: Int): Entity? {
        return database[port]
    }

    private fun publishArchiveEvent(remotePort: RemotePort, fromSelfActualization: Boolean) {
        monitoringService.publishMessage(
            LinkEvent(
                eventType = EventType.ARCHIVE_LINK_EVENT,
                targetPort = remotePort,
                closedConnectionAt = System.currentTimeMillis(),
                fromSelfActualization = fromSelfActualization
            )
        )
    }

    private fun constructNodeProfile(message: NodeDiscoveryMessage): NodeProfile {
        val tasks = message.tasks.map { Task(it.type) }
        val resources = Resources(
            message.resources.computation,
            message.resources.communication,
            message.resources.storage
        )
        return NodeProfile(message.remotePort, tasks, resources)
    }

    @PreDestroy
    fun shutdown() {
        logger.info { "Database is shutting down" }
        database.forEach { (_, entity) -> entity.communicationLink.shutdown() }
        database.clear()
    }
}
