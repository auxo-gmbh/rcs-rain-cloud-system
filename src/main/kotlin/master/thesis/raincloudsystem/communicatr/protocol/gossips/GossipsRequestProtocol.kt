package master.thesis.raincloudsystem.communicatr.protocol.gossips

import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.GossipsRequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.RequestMessage
import master.thesis.raincloudsystem.communicatr.protocol.RequestProtocol
import master.thesis.raincloudsystem.coordinatr.strategy.impl.GossipsStrategy
import master.thesis.raincloudsystem.shared.utils.decodeFromString
import master.thesis.raincloudsystem.shared.utils.encodeToString
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("Gossips")
class GossipsRequestProtocol(
    @Lazy private val gossipsStrategy: GossipsStrategy
) : RequestProtocol {

    override var type = MessageType.GOSSIPS_REQUEST

    override fun read(request: String) {
        val gossipsRequestMessage: GossipsRequestMessage = request.decodeFromString()
        gossipsStrategy.handleRequest(gossipsRequestMessage)
    }

    override fun initialWrite(request: RequestMessage): String {
        val gossipsRequestMessage = request as GossipsRequestMessage
        return gossipsRequestMessage.encodeToString()
    }

    override fun forwardWrite(request: RequestMessage): String {
        val gossipsRequestMessage = request as GossipsRequestMessage
        return gossipsRequestMessage.encodeToString()
    }
}
