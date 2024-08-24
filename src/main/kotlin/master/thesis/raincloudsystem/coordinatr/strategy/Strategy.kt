package master.thesis.raincloudsystem.coordinatr.strategy

import master.thesis.raincloudsystem.communicatr.model.message.RequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.ResponseMessage
import master.thesis.raincloudsystem.communicatr.protocol.RequestProtocol
import master.thesis.raincloudsystem.communicatr.service.PendingRequestService
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
import master.thesis.raincloudsystem.shared.task.ReceivedTask
import master.thesis.raincloudsystem.shared.task.SupportedTask
import master.thesis.raincloudsystem.shared.utils.TaskType
import master.thesis.raincloudsystem.shared.utils.isNotNull
import master.thesis.raincloudsystem.shared.utils.isPassedDeadline
import mu.KotlinLogging
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.LinkedBlockingDeque

@Component
abstract class Strategy(
    private val localNodeProfile: NodeProfile,
    private val database: Database,
    private val requestProtocol: RequestProtocol,
    private val queue: LinkedBlockingDeque<RCSTask>,
    private val rcsProperties: RCSProperties,
    private val tasksProperties: TasksProperties,
    private val supportedTasks: List<SupportedTask>,
    private val offloadedTasks: ConcurrentHashMap<String, SupportedTask>,
    private val taskScheduler: TaskScheduler,
    private val monitoringService: MonitoringService,
    private val pendingRequestService: PendingRequestService
) {

    private val logger = KotlinLogging.logger {}

    fun addTaskToQueue(task: SupportedTask) {
        task.timeToLive = calculateTimeToLive()
        queue.add(task)
        if (isNodeOverloaded()) {
            offloadTask(task)
        }
    }

    private fun calculateTimeToLive() =
        queue.size * tasksProperties.timeToLiveFactor

    fun offloadTask(task: SupportedTask) {
        val nextNodeEntity = selectNextNodeEntity(task.type)
        if (nextNodeEntity == null) {
            logger.info { "Cannot offload task with type ${task.type} because no next node found" }
        }
        nextNodeEntity.isNotNull { entity ->
            val requestId = UUID.randomUUID().toString()
            val deadline = Instant.now().plusSeconds(task.timeToLive)
            val path = listOf(localNodeProfile.remotePort)
            scheduleTaskToBeRemoved(requestId, deadline, task)
            logger.info { "Offloading task with type ${task.type} with requestId $requestId to ${entity.nodeProfile.remotePort}" }
            val requestMessage =
                constructRequestMessage(task, requestId, deadline, path)
            entity.communicationLink.write(requestProtocol.initialWrite(requestMessage))
            pendingRequestService.addPendingRequest(requestId, deadline, entity.nodeProfile.remotePort)
            publishOffloadedEvent(requestId, task, entity.nodeProfile.remotePort)
        }
    }

    fun handleRequest(request: RequestMessage) {
        try {
            val receivedTask = mapToReceivedTask(request)
            logger.info { "Received request $request" }
            if (isNodeOverloaded()) {
                logger.info { "Cannot process received $request because node is overloaded with queue size ${queue.size}. Request will be forwarded." }
                return forwardRequest(request, DetailsType.NODE_OVERLOADED)
            }
            if (request.isPassedDeadline()) {
                logger.info { "Aborting handling of request with received $request because deadline passed" }
                return publishPassedDeadlineEvent(receivedTask, request.path)
            }
            logger.info { "Adding received task with request $request to queue with size ${queue.size}" }
            scheduleDeadlineForReceivedTask(receivedTask)
            queue.add(receivedTask)
            pendingRequestService.addPendingRequest(request, request.remotePort)
            publishAcceptedTaskEvent(receivedTask, request.path)
        } catch (exception: NoSuchElementException) {
            logger.info { "Cannot process received $request because node doesn't support task. Request will be forwarded." }
            forwardRequest(request, DetailsType.NOT_SUPPORTED_TASK)
        }
    }

    private fun forwardRequest(request: RequestMessage, details: DetailsType) {
        if (request.isPassedDeadline()) {
            logger.info { "Aborting forwarding received request $request because deadline passed" }
            return publishPassedDeadlineEvent(request)
        }
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
            request.path.add(localNodeProfile.remotePort)
            logger.info { "Forwarding request $request to ${entity.nodeProfile.remotePort}" }
            entity.communicationLink.write(requestProtocol.forwardWrite(request))
            pendingRequestService.addPendingRequest(request, sourcePort)
            pendingRequestService.addPendingRequest(request, targetPort)
            publishMessagingEvent(request, details, targetPort)
        }
    }

    protected fun scheduleTaskToBeRemoved(requestId: String, deadline: Instant, task: SupportedTask) {
        offloadedTasks[requestId] = task
        val startTime = calculateCleanUpStartTime(deadline)
        taskScheduler.schedule(
            {
                logger.info { "Cleaning up offloaded task for $requestId at $startTime" }
                offloadedTasks.remove(requestId)
            },
            startTime
        )
    }

    private fun calculateCleanUpStartTime(deadline: Instant) =
        deadline.plus(rcsProperties.offloadedTasksCleanUpHours, ChronoUnit.HOURS)

    protected fun isNodeOverloaded() = queue.size > rcsProperties.queueSize

    protected fun mapToReceivedTask(message: RequestMessage): ReceivedTask {
        val supportedTask =
            supportedTasks
                .filter { it.type in rcsProperties.supportedTasks }
                .first { it.type == message.taskMessage.type }
        supportedTask.functionTime = message.functionTime
        val responseFunction = generateResponseFunction(message)
        return ReceivedTask(
            message.requestId,
            message.deadline,
            supportedTask,
            responseFunction
        )
    }

    abstract fun generateResponseFunction(message: RequestMessage): () -> Unit

    protected fun optimizePath(path: List<Int>): List<Int> {
        val nextNodeRemotePort = getClosestNodeConnectedToSourceNode(path) ?: return path
        val index = path.indexOf(nextNodeRemotePort) + 1
        return path.subList(0, index)
    }

    private fun getClosestNodeConnectedToSourceNode(path: List<Int>): Int? {
        path.forEach { port ->
            try {
                database.getCommunicationLinkByPort(port)
                return port
            } catch (_: NoCommunicationLinkException) {
            }
        }
        return null
    }

    protected fun scheduleDeadlineForReceivedTask(receivedTask: ReceivedTask) {
        taskScheduler.schedule(
            {
                if (queue.contains(receivedTask)) {
                    logger.info { "Removing received task for ${receivedTask.requestId} at ${receivedTask.deadline} because TTL reached and still not processed" }
                    queue.remove(receivedTask)
                    publishPassedDeadlineEvent(receivedTask, listOf())
                }
            },
            receivedTask.deadline
        )
    }

    private fun publishPassedDeadlineEvent(task: ReceivedTask, path: List<Int>) {
        val messagingEvent = MessagingEvent(
            requestId = task.requestId,
            taskType = task.supportedTask.type,
            path = path,
            details = DetailsType.PASSED_DEADLINE,
            targetPort = localNodeProfile.remotePort
        )
        monitoringService.publishMessage(messagingEvent)
    }

    private fun publishPassedDeadlineEvent(request: RequestMessage) {
        val messagingEvent = MessagingEvent(
            requestId = request.requestId,
            taskType = request.taskMessage.type,
            path = request.path,
            details = DetailsType.PASSED_DEADLINE,
            targetPort = localNodeProfile.remotePort
        )
        monitoringService.publishMessage(messagingEvent)
    }

    protected fun publishAcceptedTaskEvent(task: ReceivedTask, path: List<Int>) {
        val message = MessagingEvent(
            requestId = task.requestId,
            taskType = task.supportedTask.type,
            path = path,
            details = DetailsType.TASK_ACCEPTED,
            targetPort = localNodeProfile.remotePort
        )
        monitoringService.publishMessage(message)
    }

    protected fun publishResponseEvent(request: RequestMessage, path: List<Int>, targetPort: Int) {
        val messagingEvent = MessagingEvent(
            requestId = request.requestId,
            taskType = request.taskMessage.type,
            path = path,
            details = DetailsType.RESPONSE,
            targetPort = targetPort
        )
        monitoringService.publishMessage(messagingEvent)
    }

    protected fun publishNoNextLinkEvent(request: RequestMessage) {
        val messagingEvent = MessagingEvent(
            requestId = request.requestId,
            taskType = request.taskMessage.type,
            path = request.path,
            details = DetailsType.NO_NEXT_LINK,
            targetPort = localNodeProfile.remotePort
        )
        monitoringService.publishMessage(messagingEvent)
    }

    protected fun publishNoNextLinkEvent(response: ResponseMessage) {
        val messagingEvent = MessagingEvent(
            requestId = response.requestId,
            path = response.path,
            details = DetailsType.NO_NEXT_LINK,
            targetPort = localNodeProfile.remotePort
        )
        monitoringService.publishMessage(messagingEvent)
    }

    abstract fun constructRequestMessage(
        task: SupportedTask,
        requestId: String,
        deadline: Instant,
        path: List<Int>
    ): RequestMessage

    abstract fun handleResponse(response: ResponseMessage)

    abstract fun selectNextNodeEntity(taskType: TaskType, path: List<Int> = emptyList()): Entity?

    abstract fun handleLastResponse(response: ResponseMessage)

    abstract fun forwardResponse(response: ResponseMessage)

    abstract fun publishOffloadedEvent(requestId: String, task: SupportedTask, targetPort: Int)

    abstract fun publishResponseEvent(response: ResponseMessage, targetPort: Int)

    abstract fun publishMessagingEvent(request: RequestMessage, details: DetailsType, targetPort: Int)
}
