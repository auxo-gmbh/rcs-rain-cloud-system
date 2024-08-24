package master.thesis.raincloudsystem.communicatr.protocol.gossips

import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.GossipsDiscoveryRequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.RequestMessage
import master.thesis.raincloudsystem.communicatr.protocol.RequestProtocol
import master.thesis.raincloudsystem.coordinatr.strategy.impl.GossipsDiscoveryStrategy
import master.thesis.raincloudsystem.shared.utils.decodeFromString
import master.thesis.raincloudsystem.shared.utils.encodeToString
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Primary
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Primary
@Profile("Gossips")
class GossipsDiscoveryRequestProtocol(
    @Lazy
    private val gossipsDiscoveryStrategy: GossipsDiscoveryStrategy
) : RequestProtocol {

    override var type: MessageType = MessageType.GOSSIPS_DISCOVERY_REQUEST

    override fun read(request: String) {
        val gossipsDiscoveryRequestMessage: GossipsDiscoveryRequestMessage = request.decodeFromString()
        gossipsDiscoveryStrategy.handleRequest(gossipsDiscoveryRequestMessage)
    }

    override fun forwardWrite(request: RequestMessage): String {
        val gossipsDiscoveryRequestMessage = request as GossipsDiscoveryRequestMessage
        return gossipsDiscoveryRequestMessage.encodeToString()
    }

    override fun initialWrite(request: RequestMessage): String {
        val acoRequestMessage = request as GossipsDiscoveryRequestMessage
        return acoRequestMessage.encodeToString()
    }
}
