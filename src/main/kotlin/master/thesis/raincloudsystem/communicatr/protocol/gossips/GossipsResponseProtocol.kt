package master.thesis.raincloudsystem.communicatr.protocol.gossips

import master.thesis.raincloudsystem.communicatr.model.domain.InitialWriteMessage
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.GossipsResponseMessage
import master.thesis.raincloudsystem.communicatr.model.message.ResponseMessage
import master.thesis.raincloudsystem.communicatr.protocol.ResponseProtocol
import master.thesis.raincloudsystem.coordinatr.strategy.impl.GossipsStrategy
import master.thesis.raincloudsystem.shared.model.domain.NodeProfile
import master.thesis.raincloudsystem.shared.utils.decodeFromString
import master.thesis.raincloudsystem.shared.utils.encodeToString
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("Gossips")
class GossipsResponseProtocol(
    @Lazy
    private val gossipsStrategy: GossipsStrategy,
    private val localNodeProfile: NodeProfile
) : ResponseProtocol {

    override var type = MessageType.GOSSIPS_RESPONSE

    override fun read(request: String) {
        val message: GossipsResponseMessage = request.decodeFromString()
        return gossipsStrategy.handleResponse(message)
    }

    override fun initialWrite(message: InitialWriteMessage): String {
        val response = mapToResponseMessage(message)
        return response.encodeToString()
    }

    override fun forwardWrite(response: ResponseMessage): String {
        val gossipsResponseMessage = response as GossipsResponseMessage
        return gossipsResponseMessage.encodeToString()
    }

    private fun mapToResponseMessage(message: InitialWriteMessage): GossipsResponseMessage {
        return GossipsResponseMessage(
            localNodeProfile.remotePort,
            message.requestId,
            message.path.toMutableList()
        )
    }
}
