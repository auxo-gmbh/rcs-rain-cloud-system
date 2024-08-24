package master.thesis.raincloudsystem.communicatr.protocol.gossips

import master.thesis.raincloudsystem.communicatr.model.domain.GossipsDiscoveryInitialWriteMessage
import master.thesis.raincloudsystem.communicatr.model.domain.InitialWriteMessage
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.GossipsDiscoveryResponseMessage
import master.thesis.raincloudsystem.communicatr.model.message.ResponseMessage
import master.thesis.raincloudsystem.communicatr.model.message.common.TaskMessage
import master.thesis.raincloudsystem.communicatr.protocol.ResponseProtocol
import master.thesis.raincloudsystem.coordinatr.strategy.impl.GossipsDiscoveryStrategy
import master.thesis.raincloudsystem.shared.model.domain.NodeProfile
import master.thesis.raincloudsystem.shared.utils.decodeFromString
import master.thesis.raincloudsystem.shared.utils.encodeToString
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Primary
@Profile("Gossips")
class GossipsDiscoveryResponseProtocol(
    @Lazy
    private val gossipsDiscoveryStrategy: GossipsDiscoveryStrategy,
    private val localNodeProfile: NodeProfile
) : ResponseProtocol {

    override var type: MessageType = MessageType.GOSSIPS_DISCOVERY_RESPONSE

    override fun read(request: String) {
        val acoResponseMessage: GossipsDiscoveryResponseMessage = request.decodeFromString()
        gossipsDiscoveryStrategy.handleResponse(acoResponseMessage)
    }

    override fun forwardWrite(response: ResponseMessage): String {
        val acoResponseMessage = response as GossipsDiscoveryResponseMessage
        return acoResponseMessage.encodeToString()
    }

    override fun initialWrite(message: InitialWriteMessage): String {
        val gossipsDiscoveryInitialWriteMessage = message as GossipsDiscoveryInitialWriteMessage
        val response = mapToResponseMessage(gossipsDiscoveryInitialWriteMessage)
        return response.encodeToString()
    }

    private fun mapToResponseMessage(gossipsDiscoveryInitialWriteMessage: GossipsDiscoveryInitialWriteMessage):
        GossipsDiscoveryResponseMessage {
        val remotePort = localNodeProfile.remotePort
        return GossipsDiscoveryResponseMessage(
            remotePort,
            gossipsDiscoveryInitialWriteMessage.requestId,
            TaskMessage(gossipsDiscoveryInitialWriteMessage.taskType),
            gossipsDiscoveryInitialWriteMessage.path.toMutableList(),
            gossipsDiscoveryInitialWriteMessage.averageQueueRatio,
            gossipsDiscoveryInitialWriteMessage.averageResourcesRatio
        )
    }
}
