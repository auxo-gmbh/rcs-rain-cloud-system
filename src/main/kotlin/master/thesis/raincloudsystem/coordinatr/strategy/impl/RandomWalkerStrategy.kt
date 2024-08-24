package master.thesis.raincloudsystem.coordinatr.strategy.impl

import master.thesis.raincloudsystem.communicatr.model.domain.InitialWriteMessage
import master.thesis.raincloudsystem.communicatr.model.message.RWRequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.RequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.ResponseMessage
import master.thesis.raincloudsystem.communicatr.model.message.common.TaskMessage
import master.thesis.raincloudsystem.communicatr.protocol.randomwalker.RWRequestProtocol
import master.thesis.raincloudsystem.communicatr.protocol.randomwalker.RWResponseProtocol
import master.thesis.raincloudsystem.communicatr.service.PendingRequestService
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
import master.thesis.raincloudsystem.shared.utils.TaskType
import mu.KotlinLogging
import org.springframework.context.annotation.Profile
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque
import kotlin.random.Random

@Component
@Profile("Random-Walker")
class RandomWalkerStrategy(
    private val localNodeProfile: NodeProfile,
    private val database: Database,
    private val rwResponseProtocol: RWResponseProtocol,
    private val queue: LinkedBlockingDeque<RCSTask>,
    private val offloadedTasks: ConcurrentHashMap<String, SupportedTask>,
    private val pendingRequestService: PendingRequestService,
    private val monitoringService: MonitoringService,
    tasksProperties: TasksProperties,
    rwRequestProtocol: RWRequestProtocol,
    rcsProperties: RCSProperties,
    supportedTasks: List<SupportedTask>,
    taskScheduler: TaskScheduler
) : Strategy(
    localNodeProfile,
    database,
    rwRequestProtocol,
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

    override fun handleResponse(response: ResponseMessage) {
        pendingRequestService.removePendingRequest(response, response.remotePort)
        if (response.path.isEmpty()) {
            handleLastResponse(response)
        } else {
            forwardResponse(response)
        }
    }

    override fun selectNextNodeEntity(taskType: TaskType, path: List<Int>): Entity? {
        val supportingLinks =
            database.getAllEntities { entity -> !path.contains(entity.nodeProfile.remotePort) }
        if (supportingLinks.isEmpty()) {
            return null
        }
        val randomIndex = Random.nextInt(supportingLinks.size)
        return supportingLinks[randomIndex]
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

            communicationLink.write(rwResponseProtocol.forwardWrite(response))
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
            communicationLink.write(rwResponseProtocol.initialWrite(initialWriteMessage))
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
        RWRequestMessage(
            localNodeProfile.remotePort,
            requestId,
            path.toMutableList(),
            TaskMessage(task.type),
            task.functionTime!!,
            deadline
        )
}
