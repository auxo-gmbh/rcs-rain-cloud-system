package master.thesis.raincloudsystem.coordinatr.strategy.impl

import master.thesis.raincloudsystem.communicatr.model.message.GossipsDiscoveryRequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.GossipsDiscoveryResponseMessage
import master.thesis.raincloudsystem.communicatr.model.message.RequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.ResponseMessage
import master.thesis.raincloudsystem.communicatr.model.message.common.TaskMessage
import master.thesis.raincloudsystem.communicatr.protocol.gossips.GossipsDiscoveryRequestProtocol
import master.thesis.raincloudsystem.communicatr.protocol.gossips.GossipsDiscoveryResponseProtocol
import master.thesis.raincloudsystem.communicatr.service.AvoidListService
import master.thesis.raincloudsystem.monitoring.MonitoringService
import master.thesis.raincloudsystem.monitoring.StatisticsService
import master.thesis.raincloudsystem.monitoring.enums.DetailsType
import master.thesis.raincloudsystem.monitoring.message.GossipDiscoveryResponseEvent
import master.thesis.raincloudsystem.monitoring.message.GossipsDiscoveryEvent
import master.thesis.raincloudsystem.monitoring.message.MessagingEvent
import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.exception.NoCommunicationLinkException
import master.thesis.raincloudsystem.shared.model.domain.Entity
import master.thesis.raincloudsystem.shared.model.domain.NodeProfile
import master.thesis.raincloudsystem.shared.storage.Database
import master.thesis.raincloudsystem.shared.task.RCSTask
import master.thesis.raincloudsystem.shared.task.SupportedTask
import master.thesis.raincloudsystem.shared.utils.RemotePort
import master.thesis.raincloudsystem.shared.utils.TaskType
import master.thesis.raincloudsystem.shared.utils.isNotNull
import master.thesis.raincloudsystem.shared.utils.isPassedDeadline
import mu.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque

@Component
@Profile("Gossips")
class GossipsDiscoveryStrategy(
    private val localNodeProfile: NodeProfile,
    private val statisticsService: StatisticsService,
    private val database: Database,
    private val queue: LinkedBlockingDeque<RCSTask>,
    private val gossipsDiscoveryResponseProtocol: GossipsDiscoveryResponseProtocol,
    private val gossipsDiscoveryRequestProtocol: GossipsDiscoveryRequestProtocol,
    private val avoidListService: AvoidListService,
    private val rcsProperties: RCSProperties,
    private val taskScheduler: TaskScheduler,
    private val monitoringService: MonitoringService
) {

    private val logger = KotlinLogging.logger {}

    private val requestDiscoveryGossips: MutableSet<String> = ConcurrentHashMap.newKeySet()
    private val responseGossips: ConcurrentHashMap<String, MutableList<GossipsDiscoveryResponseMessage>> =
        ConcurrentHashMap()

    fun spreadGossips(requestId: String, task: SupportedTask, spreadingDiscoveryTimeToLive: Long) {
        responseGossips[requestId] = mutableListOf()
        val nextNodeEntities = selectNextNodeEntities(task.type)
        if (nextNodeEntities.isEmpty()) {
            logger.info { "Cannot spread task with type ${task.type} because no next node found" }
            return
        }

        logger.info { "Spreading Gossips with task type ${task.type} with requestId $requestId to ${nextNodeEntities.map { it.nodeProfile.remotePort }}" }

        val deadline = Instant.now().plusSeconds(spreadingDiscoveryTimeToLive)
        val path = listOf(localNodeProfile.remotePort)
        nextNodeEntities.forEach { entity ->
            val requestMessage = constructRequestMessage(task, requestId, deadline, path)
            entity.communicationLink.write(gossipsDiscoveryRequestProtocol.initialWrite(requestMessage))
            publishGossipsDiscoveryRequestEvent(requestMessage, entity.nodeProfile.remotePort)
        }
    }

    private fun constructRequestMessage(
        task: SupportedTask,
        requestId: String,
        deadline: Instant,
        path: List<Int>
    ) = GossipsDiscoveryRequestMessage(
        localNodeProfile.remotePort,
        requestId,
        path.toMutableList(),
        TaskMessage(task.type),
        deadline
    )

    fun collectAndProcessGossips(requestId: String): Optional<GossipsDiscoveryResponseMessage> {
        val receivedResponses = responseGossips[requestId]!!

        val rankedResponses = receivedResponses.sortedByDescending { highestRatiosAndShortestPath(it) }
        publishGossipsDiscoveryResponses(requestId, rankedResponses)
        return if (rankedResponses.isEmpty()) Optional.empty() else Optional.of(rankedResponses.first())
    }

    private fun highestRatiosAndShortestPath(response: GossipsDiscoveryResponseMessage): Double {
        return response.averageQueueRatio * response.averageResourcesRatio * (1.0 / response.path.size)
    }

    fun selectNextNodeEntities(taskType: TaskType, path: List<Int> = emptyList()): List<Entity> {
        return database.getAllEntities { entity ->
            !avoidListService.isPartOfAvoidList(entity.nodeProfile.remotePort) &&
                !path.contains(entity.nodeProfile.remotePort)
        }
    }

    fun handleRequest(request: GossipsDiscoveryRequestMessage) {
        val requestId = request.requestId
        if (requestDiscoveryGossips.contains(requestId)) {
            logger.info { "Not spreading $request because already heard it" }
            return
        }
        if (request.isPassedDeadline()) {
            logger.info { "Aborting handling of request with received $request because deadline passed" }
            return
        }

        requestDiscoveryGossips.add(requestId)
        scheduleRequestGossipToBeRemoved(requestId, request.deadline)
        forwardSpreadingDiscoveryRequest(request)

        if (!supportsTask(request.taskMessage.type)) {
            logger.info { "Cannot process received $request because node doesn't support task. Request will be forwarded." }
            return
        }
        if (isNodeOverloaded()) {
            logger.info { "Cannot process received $request because node is overloaded with queue size ${queue.size}. Request will be forwarded." }
            return
        }

        sendBackDiscoveryResponse(request)
    }

    private fun scheduleRequestGossipToBeRemoved(requestId: String, deadline: Instant) {
        val startTime = calculateCleanUpStartTime(deadline)
        taskScheduler.schedule(
            {
                logger.info { "Cleaning up Request gossip $requestId at $startTime" }
                requestDiscoveryGossips.remove(requestId)
            },
            startTime
        )
    }

    private fun calculateCleanUpStartTime(deadline: Instant) =
        deadline.plus(rcsProperties.offloadedTasksCleanUpHours, ChronoUnit.HOURS)

    private fun forwardSpreadingDiscoveryRequest(request: GossipsDiscoveryRequestMessage) {
        val (_, requestId, path, taskMessage, deadline, _, _) = request
        val nextNodeEntities = selectNextNodeEntities(taskMessage.type, path)
        if (nextNodeEntities.isEmpty()) {
            logger.info { "Cannot spread task with type ${taskMessage.type} because no next node found" }
            return
        }

        val localPort = localNodeProfile.remotePort
        val appendedPath = appendItselfToPath(request)

        logger.info {
            "Spreading Gossips with task type ${taskMessage.type} with requestId $requestId to ${nextNodeEntities.map { it.nodeProfile.remotePort }}"
        }

        nextNodeEntities.forEach { entity ->
            val requestMessage = GossipsDiscoveryRequestMessage(
                localPort,
                requestId,
                appendedPath,
                taskMessage,
                deadline
            )
            entity.communicationLink.write(gossipsDiscoveryRequestProtocol.forwardWrite(requestMessage))
            publishGossipsDiscoveryRequestEvent(requestMessage, entity.nodeProfile.remotePort)
        }
    }

    private fun sendBackDiscoveryResponse(request: GossipsDiscoveryRequestMessage) {
        val response = buildResponseMessage(request)
        writeBackResponseTo(request.remotePort, response)
        publishGossipsDiscoveryResponseEvent(response, request.remotePort)
    }

    private fun buildResponseMessage(request: GossipsDiscoveryRequestMessage): GossipsDiscoveryResponseMessage {
        return GossipsDiscoveryResponseMessage(
            localNodeProfile.remotePort,
            request.requestId,
            request.taskMessage,
            appendItselfToPath(request),
            calculateAverageQueueRatio(),
            calculateAverageResourcesRatio()
        )
    }

    private fun appendItselfToPath(request: GossipsDiscoveryRequestMessage): MutableList<RemotePort> {
        val appendedPath = request.path
        if (appendedPath.last() != localNodeProfile.remotePort) {
            appendedPath.add(localNodeProfile.remotePort)
        }
        return appendedPath
    }

    private fun writeBackResponseTo(remotePort: RemotePort, response: GossipsDiscoveryResponseMessage) {
        try {
            val communicationLink = database.getCommunicationLinkByPort(remotePort)
            communicationLink.write(
                gossipsDiscoveryResponseProtocol.forwardWrite(response)
            )
        } catch (exception: NoCommunicationLinkException) {
            logger.info { "Cannot send back response $response because $remotePort not found" }
        }
    }

    private fun calculateAverageQueueRatio(): Double {
        val averageQueueOccupation = statisticsService.getAveragePersistedQueueOccupation()
        return 1 - (averageQueueOccupation / rcsProperties.queueSize)
    }

    private fun calculateAverageResourcesRatio(): Double {
        val resourcesList = listOf(
            rcsProperties.communication,
            rcsProperties.computation,
            rcsProperties.storage
        )
        return 1.0 / resourcesList.average()
    }

    fun handleResponse(gossipsDiscoveryResponseMessage: GossipsDiscoveryResponseMessage) {
        if (gossipsDiscoveryResponseMessage.path.indexOf(localNodeProfile.remotePort) == 0) {
            handleLastResponse(gossipsDiscoveryResponseMessage)
        } else {
            forwardResponse(gossipsDiscoveryResponseMessage)
        }
    }

    fun handleLastResponse(gossipsDiscoveryResponseMessage: GossipsDiscoveryResponseMessage) {
        responseGossips[gossipsDiscoveryResponseMessage.requestId].isNotNull { list ->
            list.add(gossipsDiscoveryResponseMessage)
        }
    }

    fun forwardResponse(gossipsDiscoveryResponseMessage: GossipsDiscoveryResponseMessage) {
        try {
            val path = gossipsDiscoveryResponseMessage.path
            val targetPort = path[path.indexOf(localNodeProfile.remotePort) - 1]

            val communicationLink = database.getCommunicationLinkByPort(targetPort)
            gossipsDiscoveryResponseMessage.remotePort = localNodeProfile.remotePort

            logger.info { "Forwarding $gossipsDiscoveryResponseMessage to $targetPort" }
            communicationLink.write(gossipsDiscoveryResponseProtocol.forwardWrite(gossipsDiscoveryResponseMessage))
            publishGossipsDiscoveryResponseEvent(gossipsDiscoveryResponseMessage, targetPort)
        } catch (exception: NoCommunicationLinkException) {
            logger.info { "Cannot forward $gossipsDiscoveryResponseMessage because ${exception.message}" }
        }
    }

    private fun supportsTask(taskType: TaskType) = taskType in rcsProperties.supportedTasks

    private fun isNodeOverloaded() = queue.size > rcsProperties.queueSize

    private fun publishGossipsDiscoveryResponses(
        requestId: String,
        responses: List<GossipsDiscoveryResponseMessage>
    ) {
        val responsesEvent =
            responses.map { GossipDiscoveryResponseEvent(it.path, it.averageQueueRatio, it.averageResourcesRatio) }
        val event = GossipsDiscoveryEvent(
            requestId = requestId,
            responses = responsesEvent
        )
        monitoringService.publishMessage(event)
    }

    private fun publishGossipsDiscoveryResponseEvent(response: ResponseMessage, targetPort: Int) {
        val messagingEvent = MessagingEvent(
            requestId = response.requestId,
            path = response.path,
            details = DetailsType.GOSSIPS_DISCOVERY_RESPONSE,
            targetPort = targetPort
        )
        monitoringService.publishMessage(messagingEvent)
    }

    private fun publishGossipsDiscoveryRequestEvent(request: RequestMessage, targetPort: Int) {
        val messagingEvent = MessagingEvent(
            requestId = request.requestId,
            taskType = request.taskMessage.type,
            path = request.path,
            details = DetailsType.GOSSIPS_DISCOVERY_REQUEST,
            targetPort = targetPort
        )
        monitoringService.publishMessage(messagingEvent)
    }
}
