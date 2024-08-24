package master.thesis.raincloudsystem.communicatr.service

import master.thesis.raincloudsystem.communicatr.config.properties.SelfActualizationProperties
import master.thesis.raincloudsystem.communicatr.model.message.RequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.ResponseMessage
import master.thesis.raincloudsystem.shared.utils.RemotePort
import mu.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

@Component
class PendingRequestService(
    @Lazy
    private val avoidListService: AvoidListService,
    private val taskScheduler: TaskScheduler,
    private val selfActualizationProperties: SelfActualizationProperties
) {

    private val logger = KotlinLogging.logger {}

    private val pendingRequests = ConcurrentHashMap<RemotePort, MutableList<String>>()
    private val mutex = Semaphore(1)

    fun addPendingRequest(message: RequestMessage, remotePort: RemotePort) {
        addPendingRequest(message.requestId, message.deadline, remotePort)
    }

    fun addPendingRequest(requestId: String, deadline: Instant, remotePort: RemotePort) {
        runMutuallyExclusive {
            addPendingRequestToList(requestId, remotePort)
            scheduleCleanup(requestId, deadline, remotePort)
        }
    }

    fun removePendingRequest(message: ResponseMessage, remotePort: RemotePort) {
        removePendingRequest(message.requestId, remotePort)
    }

    fun removePendingRequest(requestId: String, remotePort: RemotePort) {
        var list = mutableListOf<String>()
        runMutuallyExclusive {
            list = pendingRequests.getOrDefault(remotePort, list)
            list.remove(requestId)
        }
        if (list.isEmpty() && avoidListService.isPartOfAvoidList(remotePort)) {
            avoidListService.switchFromAvoidToBlockList(remotePort)
        }
        logger.info { "Removed pending request $requestId for $remotePort" }
    }

    fun hasPendingRequestsToRemotePort(remotePort: RemotePort): Boolean {
        if (pendingRequests.containsKey(remotePort)) {
            return pendingRequests[remotePort]!!.isNotEmpty()
        }
        return false
    }

    private fun addPendingRequestToList(requestId: String, remotePort: RemotePort) {
        pendingRequests.compute(remotePort) { _, value ->
            if (value == null) {
                mutableListOf(requestId)
            } else {
                value.add(requestId)
                value
            }
        }
    }

    private fun scheduleCleanup(requestId: String, deadline: Instant, remotePort: RemotePort) {
        val extendedDeadline = deadline.plus(selfActualizationProperties.pendingTasksCleanUpMinutes, ChronoUnit.MINUTES)
        taskScheduler.schedule(
            {
                var list = mutableListOf<String>()
                runMutuallyExclusive {
                    list = pendingRequests.getOrDefault(remotePort, list)
                    list.remove(requestId)
                }
                if (list.isEmpty() && avoidListService.isPartOfAvoidList(remotePort)) {
                    avoidListService.switchFromAvoidToBlockList(remotePort)
                }
                logger.info { "Cleaned up pending request $requestId at $extendedDeadline" }
            },
            extendedDeadline
        )
    }

    private fun runMutuallyExclusive(function: () -> Unit) {
        try {
            mutex.acquire()
            function.invoke()
        } finally {
            mutex.release()
        }
    }
}
