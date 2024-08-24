package master.thesis.raincloudsystem.communicatr.service

import master.thesis.raincloudsystem.communicatr.model.message.PendingAvoidRequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.PendingAvoidResponseMessage
import master.thesis.raincloudsystem.communicatr.protocol.selfactualization.PendingAvoidRequestProtocol
import master.thesis.raincloudsystem.communicatr.protocol.selfactualization.PendingAvoidResponseProtocol
import master.thesis.raincloudsystem.monitoring.MonitoringService
import master.thesis.raincloudsystem.monitoring.enums.DetailsType
import master.thesis.raincloudsystem.monitoring.message.MessagingEvent
import master.thesis.raincloudsystem.shared.exception.NoCommunicationLinkException
import master.thesis.raincloudsystem.shared.model.domain.NodeProfile
import master.thesis.raincloudsystem.shared.storage.Database
import master.thesis.raincloudsystem.shared.utils.RemotePort
import mu.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class AvoidListService(
    private val blockListService: BlockListService,
    @Lazy
    private val selfActualizationService: SelfActualizationService,
    private val pendingRequestService: PendingRequestService,
    private val localNodeProfile: NodeProfile,
    @Lazy
    private val database: Database,
    private val pendingAvoidRequestProtocol: PendingAvoidRequestProtocol,
    private val pendingAvoidResponseProtocol: PendingAvoidResponseProtocol,
    private val monitoringService: MonitoringService
) {

    private val logger = KotlinLogging.logger {}

    private val pendingList = mutableSetOf<RemotePort>()
    private val avoidList = mutableSetOf<RemotePort>()

    fun addAllToPendingAvoidList(list: Set<RemotePort>) {
        list.filterNot { isPartOfAvoidList(it) }
            .forEach { addToPendingAvoidList(it) }
    }

    fun isPartOfAvoidList(remotePort: RemotePort) = avoidList.contains(remotePort)

    fun switchFromAvoidToBlockList(remotePort: RemotePort) {
        avoidList.remove(remotePort)
        logger.info { "Removed $remotePort from avoid list because it has no pending requests anymore or avoided candidate disconnected" }
        blockListService.addToBlockList(remotePort)
    }

    fun handleRequest(request: PendingAvoidRequestMessage) {
        val response = constructResponseMessage(request)
        ignoreNoCommunicationLinkException {
            val remotePort = request.remotePort
            val communicationLink = database.getCommunicationLinkByPort(remotePort)
            communicationLink.write(pendingAvoidResponseProtocol.write(response))
            if (response.isAccepted) {
                avoidList.add(remotePort)
                logger.info { "Added $remotePort to avoid list because node is also outlier" }
            } else {
                logger.info { "Reject adding $remotePort to avoid list for request $request" }
            }
            publishPendingAvoidResponseMessage(remotePort, response.isAccepted)
        }
    }

    private fun addToPendingAvoidList(remotePort: RemotePort) {
        ignoreNoCommunicationLinkException {
            val communicationLink = database.getCommunicationLinkByPort(remotePort)
            val request = PendingAvoidRequestMessage(localNodeProfile.remotePort)

            communicationLink.write(pendingAvoidRequestProtocol.write(request))
            pendingList.add(remotePort)
            logger.info { "Added $remotePort to pending avoid list because self-actualization computed $remotePort to be removed" }
            publishPendingAvoidRequestMessage(remotePort)
        }
    }

    private fun publishPendingAvoidRequestMessage(remotePort: RemotePort) {
        val message = MessagingEvent(
            details = DetailsType.PENDING_AVOID_REQUEST,
            targetPort = remotePort
        )
        monitoringService.publishMessage(message)
    }

    private fun publishPendingAvoidResponseMessage(remotePort: RemotePort, isAccepted: Boolean) {
        val message = MessagingEvent(
            details = if (isAccepted) DetailsType.PENDING_AVOID_ACCEPTED_RESPONSE else DetailsType.PENDING_AVOID_REJECTED_RESPONSE,
            targetPort = remotePort
        )
        monitoringService.publishMessage(message)
    }

    private fun constructResponseMessage(request: PendingAvoidRequestMessage): PendingAvoidResponseMessage {
        val removableEdges = selfActualizationService.getRemovableEdges()
        return PendingAvoidResponseMessage(
            localNodeProfile.remotePort,
            removableEdges.contains(request.remotePort)
        )
    }

    fun handleResponse(response: PendingAvoidResponseMessage) {
        val remotePort = response.remotePort
        pendingList.remove(remotePort)
        logger.info { "Removed $remotePort from pending list because received $response" }

        if (!response.isAccepted) {
            logger.info { "Reject adding $remotePort to avoid list for $response" }
            return
        }

        if (!pendingRequestService.hasPendingRequestsToRemotePort(remotePort)) {
            logger.info { "Skipping adding to avoid list and directly adding $remotePort to block list because avoid-request accepted and no pending tasks" }
            blockListService.addToBlockList(remotePort)
            return
        }

        avoidList.add(remotePort)
        logger.info { "Added $remotePort to avoid list because avoid-request accepted" }
    }

    private fun ignoreNoCommunicationLinkException(function: () -> Unit) {
        try {
            function.invoke()
        } catch (_: NoCommunicationLinkException) {
        }
    }
}
