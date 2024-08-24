package master.thesis.raincloudsystem.coordinatr.strategy.impl

import master.thesis.raincloudsystem.communicatr.model.domain.InitialWriteMessage
import master.thesis.raincloudsystem.communicatr.model.message.GossipsDiscoveryResponseMessage
import master.thesis.raincloudsystem.communicatr.model.message.GossipsRequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.RequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.ResponseMessage
import master.thesis.raincloudsystem.communicatr.model.message.common.TaskMessage
import master.thesis.raincloudsystem.communicatr.protocol.gossips.GossipsRequestProtocol
import master.thesis.raincloudsystem.communicatr.protocol.gossips.GossipsResponseProtocol
import master.thesis.raincloudsystem.communicatr.service.AvoidListService
import master.thesis.raincloudsystem.communicatr.service.PendingRequestService
import master.thesis.raincloudsystem.coordinatr.config.properties.GossipsProperties
import master.thesis.raincloudsystem.coordinatr.strategy.Strategy
import master.thesis.raincloudsystem.monitoring.MonitoringService
import master.thesis.raincloudsystem.monitoring.enums.DetailsType
import master.thesis.raincloudsystem.monitoring.message.MessagingEvent
import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.config.properties.TasksProperties
import master.thesis.raincloudsystem.shared.exception.NoCommunicationLinkException
import master.thesis.raincloudsystem.shared.model.domain.Entity
import master.thesis.raincloudsystem.shared.model.domain.NodeProfile
import master.thesis.raincloudsystem.shared.storage.Database
import master.thesis.raincloudsystem.shared.task.RCSTask
import master.thesis.raincloudsystem.shared.task.SupportedTask
import master.thesis.raincloudsystem.shared.utils.RemotePort
import master.thesis.raincloudsystem.shared.utils.TaskType
import master.thesis.raincloudsystem.shared.utils.isNotNull
import mu.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque

@Component
@Profile("Gossips")
class GossipsStrategy(
    private val localNodeProfile: NodeProfile,
    private val database: Database,
    private val queue: LinkedBlockingDeque<RCSTask>,
    private val offloadedTasks: ConcurrentHashMap<String, SupportedTask>,
    private val monitoringService: MonitoringService,
    private val pendingRequestService: PendingRequestService,
    private val gossipsResponseProtocol: GossipsResponseProtocol,
    private val avoidListService: AvoidListService,
    private val gossipsDiscoveryStrategy: GossipsDiscoveryStrategy,
    private val taskScheduler: TaskScheduler,
    private val gossipsProperties: GossipsProperties,
    private val gossipsRequestProtocol: GossipsRequestProtocol,
    private val gossipsQuality: ConcurrentHashMap<RemotePort, Int>,
    tasksProperties: TasksProperties,
    rcsProperties: RCSProperties,
    supportedTasks: List<SupportedTask>
) : Strategy(
    localNodeProfile,
    database,
    gossipsRequestProtocol,
    queue,
    rcsProperties,
    tasksProperties,
    supportedTasks,
    offloadedTasks,
    taskScheduler,
    monitoringService,
    pendingRequestService
) {

    private val logger = KotlinLogging.logger {}

    override fun offloadTask(task: SupportedTask) {
        val requestId = UUID.randomUUID().toString()
        val spreadingTTL = calculateSpreadingTTL(task)
        gossipsDiscoveryStrategy.spreadGossips(requestId, task, spreadingTTL)
        taskScheduler.schedule(
            { sendRequest(requestId, task) },
            Instant.now().plusSeconds(spreadingTTL)
        )
    }

    private fun calculateSpreadingTTL(task: SupportedTask) =
        (task.timeToLive * gossipsProperties.spreadingTTLFactor).toLong()

    private fun sendRequest(requestId: String, task: SupportedTask) {
        val gossipsResponse = gossipsDiscoveryStrategy.collectAndProcessGossips(requestId)
        gossipsResponse.ifPresentOrElse(
            { sendExecuteTaskRequest(it, task) },
            { logger.info { "No gossip response received for $requestId" } }
        )
    }

    private fun sendExecuteTaskRequest(
        gossipsDiscoveryResponseMessage: GossipsDiscoveryResponseMessage,
        task: SupportedTask
    ) {
        val (_, requestId, taskMessage, path, _, _, _) = gossipsDiscoveryResponseMessage
        val nextNodeEntity = selectNextNodeEntity(taskMessage.type, path)
        if (nextNodeEntity == null) {
            logger.info { "Cannot send task request with type ${taskMessage.type} because no next node found for path $path" }
        }
        nextNodeEntity.isNotNull { entity ->
            val deadline = Instant.now().plusSeconds(task.timeToLive)
            scheduleTaskToBeRemoved(requestId, deadline, task)
            logger.info { "Send task request with type ${task.type} with requestId $requestId to ${entity.nodeProfile.remotePort}" }
            val requestMessage = constructRequestMessage(task, requestId, deadline, path)
            entity.communicationLink.write(gossipsRequestProtocol.initialWrite(requestMessage))
            pendingRequestService.addPendingRequest(requestId, deadline, entity.nodeProfile.remotePort)
            publishOffloadedEvent(requestId, task, entity.nodeProfile.remotePort)
        }
    }

    override fun handleResponse(response: ResponseMessage) {
        logger.info { "Handle response $response" }
        pendingRequestService.removePendingRequest(response, response.remotePort)
        gossipsQuality.compute(response.remotePort) { _, value ->
            return@compute if (value == null) 1 else value + 1
        }
        if (response.path.isEmpty()) {
            handleLastResponse(response)
        } else {
            forwardResponse(response)
        }
    }

    override fun selectNextNodeEntity(taskType: TaskType, path: List<Int>): Entity? {
        val currentNodeIndex = path.indexOf(localNodeProfile.remotePort)
        val nextNode = path[currentNodeIndex + 1]
        val nextNodeEntity = database.getEntityByPort(nextNode)
        nextNodeEntity.isNotNull {
            if (!avoidListService.isPartOfAvoidList(it.nodeProfile.remotePort)) {
                return nextNodeEntity
            }
        }
        return null
    }

    override fun handleRequest(request: RequestMessage) {
        if (checkIfLastNode(localNodeProfile.remotePort, request.path)) {
            handleLastRequest(request)
        } else {
            forwardRequest(request)
        }
    }

    private fun checkIfLastNode(remotePort: RemotePort, path: List<Int>) = path.last() == remotePort

    private fun handleLastRequest(request: RequestMessage) {
        try {
            val receivedTask = mapToReceivedTask(request)
            logger.info { "Received request $request" }
            if (isNodeOverloaded()) {
                logger.info { "Cannot process received $request because node is overloaded with queue size ${queue.size}." }
                return
            }
            logger.info { "Adding received task with request $request to queue with size ${queue.size}" }
            scheduleDeadlineForReceivedTask(receivedTask)
            queue.add(receivedTask)
            pendingRequestService.addPendingRequest(request, request.remotePort)
            publishAcceptedTaskEvent(receivedTask, request.path)
        } catch (exception: NoSuchElementException) {
            logger.error { "Cannot process received $request because node doesn't support task" }
            throw RuntimeException(exception)
        }
    }

    private fun forwardRequest(request: RequestMessage) {
        val alreadySelectedLinks = request.path
        val nextNodeEntity = selectNextNodeEntity(request.taskMessage.type, alreadySelectedLinks)
        if (nextNodeEntity == null) {
            logger.info { "Cannot forward $request because no next node found" }
            publishNoNextLinkEvent(request)
        }
        nextNodeEntity.isNotNull { entity ->
            val sourcePort = request.remotePort
            val targetPort = entity.nodeProfile.remotePort
            request.remotePort = localNodeProfile.remotePort
            logger.info { "Forwarding request $request to ${entity.nodeProfile.remotePort}" }
            entity.communicationLink.write(gossipsRequestProtocol.forwardWrite(request))
            pendingRequestService.addPendingRequest(request, sourcePort)
            pendingRequestService.addPendingRequest(request, targetPort)
            publishMessagingEvent(request, DetailsType.GOSSIPS_REQUEST, targetPort)
        }
    }

    override fun handleLastResponse(response: ResponseMessage) {
        logger.info { "Received executed offloaded task with $response" }
        if (offloadedTasks.contains(response.requestId)) {
            val task = offloadedTasks[response.requestId]
            queue.remove(task)
        }
    }

    override fun forwardResponse(response: ResponseMessage) {
        try {
            val optimizedPath = optimizePath(response.path)
            val targetPort = optimizedPath[optimizedPath.size - 1]
            val communicationLink = database.getCommunicationLinkByPort(targetPort)

            response.path = optimizedPath.dropLast(1).toMutableList()
            response.remotePort = localNodeProfile.remotePort

            logger.info { "Forwarding $response to $targetPort" }

            communicationLink.write(gossipsResponseProtocol.forwardWrite(response))
            pendingRequestService.removePendingRequest(response, targetPort)
            publishResponseEvent(response, targetPort)
        } catch (exception: NoCommunicationLinkException) {
            logger.info { "Cannot forward $response because ${exception.message}" }
            publishNoNextLinkEvent(response)
        }
    }

    override fun generateResponseFunction(message: RequestMessage): () -> Unit = {
        try {
            val requestId = message.requestId
            val optimizedPath = optimizePath(message.path)
            val updatedPath = optimizedPath.dropLast(1)
            val targetPort = optimizedPath[optimizedPath.size - 1]
            val communicationLink = database.getCommunicationLinkByPort(targetPort)
            val initialWriteMessage = InitialWriteMessage(requestId, updatedPath)
            communicationLink.write(gossipsResponseProtocol.initialWrite(initialWriteMessage))
            pendingRequestService.removePendingRequest(requestId, targetPort)
            publishResponseEvent(message, updatedPath, targetPort)
        } catch (ex: NoCommunicationLinkException) {
            logger.info { "Cannot send response for request ${message.requestId} and path ${message.path} because no next node found" }
            publishNoNextLinkEvent(message)
        }
    }

    override fun publishOffloadedEvent(requestId: String, task: SupportedTask, targetPort: Int) {
        val messagingEvent = MessagingEvent(
            requestId = requestId,
            taskType = task.type,
            path = listOf(localNodeProfile.remotePort),
            details = DetailsType.OFFLOADED,
            targetPort = targetPort,
            functionTime = task.functionTime
        )
        monitoringService.publishMessage(messagingEvent)
    }

    override fun publishResponseEvent(response: ResponseMessage, targetPort: Int) {
        val messagingEvent = MessagingEvent(
            requestId = response.requestId,
            path = response.path,
            details = DetailsType.RESPONSE,
            targetPort = targetPort
        )
        monitoringService.publishMessage(messagingEvent)
    }

    override fun publishMessagingEvent(request: RequestMessage, details: DetailsType, targetPort: Int) {
        val messagingEvent = MessagingEvent(
            requestId = request.requestId,
            taskType = request.taskMessage.type,
            path = request.path,
            details = details,
            targetPort = targetPort,
            functionTime = request.functionTime
        )
        monitoringService.publishMessage(messagingEvent)
    }

    override fun constructRequestMessage(
        task: SupportedTask,
        requestId: String,
        deadline: Instant,
        path: List<Int>
    ): RequestMessage =
        GossipsRequestMessage(
            localNodeProfile.remotePort,
            requestId,
            path.toMutableList(),
            TaskMessage(task.type),
            deadline,
            task.functionTime!!
        )
}
