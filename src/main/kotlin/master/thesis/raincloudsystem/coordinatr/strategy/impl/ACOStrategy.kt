package master.thesis.raincloudsystem.coordinatr.strategy.impl

import master.thesis.raincloudsystem.communicatr.model.domain.ACOInitialWriteMessage
import master.thesis.raincloudsystem.communicatr.model.message.ACORequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.ACOResponseMessage
import master.thesis.raincloudsystem.communicatr.model.message.RequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.ResponseMessage
import master.thesis.raincloudsystem.communicatr.model.message.common.TaskMessage
import master.thesis.raincloudsystem.communicatr.protocol.aco.ACORequestProtocol
import master.thesis.raincloudsystem.communicatr.protocol.aco.ACOResponseProtocol
import master.thesis.raincloudsystem.communicatr.service.PendingRequestService
import master.thesis.raincloudsystem.coordinatr.strategy.Strategy
import master.thesis.raincloudsystem.monitoring.MonitoringService
import master.thesis.raincloudsystem.monitoring.StatisticsService
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
import master.thesis.raincloudsystem.shared.utils.TaskType
import mu.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque

@Component
@Profile("ACO")
class ACOStrategy(
    private val acoAlgorithm: ACOAlgorithm,
    private val localNodeProfile: NodeProfile,
    private val statisticsService: StatisticsService,
    private val database: Database,
    private val acoResponseProtocol: ACOResponseProtocol,
    private val queue: LinkedBlockingDeque<RCSTask>,
    private val offloadedTasks: ConcurrentHashMap<String, SupportedTask>,
    private val monitoringService: MonitoringService,
    private val pendingRequestService: PendingRequestService,
    acoRequestProtocol: ACORequestProtocol,
    rcsProperties: RCSProperties,
    tasksProperties: TasksProperties,
    supportedTasks: List<SupportedTask>,
    taskScheduler: TaskScheduler
) : Strategy(
    localNodeProfile,
    database,
    acoRequestProtocol,
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

    override fun selectNextNodeEntity(taskType: TaskType, path: List<Int>): Entity? {
        val targetPort = acoAlgorithm.getNextCommunicationLinkPort(taskType, path)
        return database.getEntityByPort(targetPort)
    }

    override fun constructRequestMessage(
        task: SupportedTask,
        requestId: String,
        deadline: Instant,
        path: List<Int>
    ): RequestMessage =
        ACORequestMessage(
            localNodeProfile.remotePort,
            requestId,
            path.toMutableList(),
            TaskMessage(task.type),
            deadline,
            task.functionTime!!
        )

    override fun generateResponseFunction(message: RequestMessage): () -> Unit = {
        try {
            val requestId = message.requestId

            val path = message.path
            val targetPort = path.last()

            val communicationLink = database.getCommunicationLinkByPort(targetPort)

            val averageQueueOccupation = statisticsService.getAveragePersistedQueueOccupation()
            val numberOfCommunicationLinks = database.getAllEntities().size
            val resources = localNodeProfile.resources

            val qualityPheromones =
                acoAlgorithm.calculateQualityPheromones(averageQueueOccupation, numberOfCommunicationLinks, resources)

            val acoInitialWriteMessage =
                ACOInitialWriteMessage(requestId, path, message.taskMessage.type, qualityPheromones)

            communicationLink.write(acoResponseProtocol.initialWrite(acoInitialWriteMessage))
            pendingRequestService.removePendingRequest(requestId, targetPort)
            publishResponseEvent(message, path, targetPort, qualityPheromones)
        } catch (ex: NoCommunicationLinkException) {
            logger.info { "Cannot send response for request ${message.requestId} and path ${message.path} because no next node found" }
            publishNoNextLinkEvent(message)
        }
    }

    override fun handleResponse(response: ResponseMessage) {
        pendingRequestService.removePendingRequest(response, response.remotePort)
        if (response.path.indexOf(localNodeProfile.remotePort) == 0) {
            handleLastResponse(response)
        } else {
            forwardResponse(response)
        }
    }

    override fun handleLastResponse(response: ResponseMessage) {
        val acoResponseMessage = response as ACOResponseMessage

        acoAlgorithm.updatePheromoneTables(
            localNodeProfile.remotePort,
            acoResponseMessage.remotePort,
            acoResponseMessage.taskMessage.type,
            acoResponseMessage.path,
            acoResponseMessage.qualityPheromones
        )

        logger.info { "Received executed offloaded task with $acoResponseMessage" }
        if (offloadedTasks.contains(acoResponseMessage.requestId)) {
            val task = offloadedTasks[acoResponseMessage.requestId]
            queue.remove(task)
        }
    }

    override fun forwardResponse(response: ResponseMessage) {
        val acoResponseMessage = response as ACOResponseMessage

        try {
            val path = acoResponseMessage.path
            val targetPort = path[path.indexOf(localNodeProfile.remotePort) - 1]

            acoAlgorithm.updatePheromoneTables(
                localNodeProfile.remotePort,
                acoResponseMessage.remotePort,
                acoResponseMessage.taskMessage.type,
                path,
                acoResponseMessage.qualityPheromones
            )

            val communicationLink = database.getCommunicationLinkByPort(targetPort)
            acoResponseMessage.remotePort = localNodeProfile.remotePort

            logger.info { "Forwarding $acoResponseMessage to $targetPort" }
            pendingRequestService.removePendingRequest(response, targetPort)
            communicationLink.write(acoResponseProtocol.forwardWrite(acoResponseMessage))

            publishResponseEvent(acoResponseMessage, targetPort)
        } catch (exception: NoCommunicationLinkException) {
            logger.info { "Cannot forward $acoResponseMessage because ${exception.message}" }
            publishNoNextLinkEvent(response)
        }
    }

    private fun publishResponseEvent(
        request: RequestMessage,
        path: List<Int>,
        targetPort: Int,
        qualityPheromones: Double
    ) {
        val messagingEvent = MessagingEvent(
            requestId = request.requestId,
            taskType = request.taskMessage.type,
            path = path,
            details = DetailsType.RESPONSE,
            targetPort = targetPort,
            qualityPheromones = qualityPheromones
        )
        monitoringService.publishMessage(messagingEvent)
    }

    override fun publishResponseEvent(response: ResponseMessage, targetPort: Int) {
        val acoResponseMessage = response as ACOResponseMessage
        val messagingEvent = MessagingEvent(
            requestId = acoResponseMessage.requestId,
            path = acoResponseMessage.path,
            details = DetailsType.RESPONSE,
            targetPort = targetPort,
            qualityPheromones = acoResponseMessage.qualityPheromones
        )
        monitoringService.publishMessage(messagingEvent)
    }

    override fun publishOffloadedEvent(requestId: String, task: SupportedTask, targetPort: Int) {
        val taskType = task.type
        val messagingEvent = MessagingEvent(
            requestId = requestId,
            taskType = taskType,
            path = listOf(localNodeProfile.remotePort),
            details = DetailsType.OFFLOADED,
            targetPort = targetPort,
            pheromonesEdges = acoAlgorithm.getEdgesByTaskType(taskType),
            functionTime = task.functionTime
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
            pheromonesEdges = acoAlgorithm.getEdgesByTaskType(request.taskMessage.type),
            functionTime = request.functionTime
        )
        monitoringService.publishMessage(messagingEvent)
    }
}
