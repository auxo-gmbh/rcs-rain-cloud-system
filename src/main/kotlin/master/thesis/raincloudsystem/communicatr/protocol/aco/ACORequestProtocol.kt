package master.thesis.raincloudsystem.communicatr.protocol.aco

import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.ACORequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.RequestMessage
import master.thesis.raincloudsystem.communicatr.protocol.RequestProtocol
import master.thesis.raincloudsystem.coordinatr.strategy.Strategy
import master.thesis.raincloudsystem.shared.utils.decodeFromString
import master.thesis.raincloudsystem.shared.utils.encodeToString
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("ACO")
class ACORequestProtocol(
    @Lazy private val acoStrategy: Strategy
) : RequestProtocol {

    override var type: MessageType = MessageType.ACO_REQUEST

    override fun read(request: String) {
        val acoRequestMessage: ACORequestMessage = request.decodeFromString()
        acoStrategy.handleRequest(acoRequestMessage)
    }

    override fun forwardWrite(request: RequestMessage): String {
        val acoRequestMessage = request as ACORequestMessage
        return acoRequestMessage.encodeToString()
    }

    override fun initialWrite(request: RequestMessage): String {
        val acoRequestMessage = request as ACORequestMessage
        return acoRequestMessage.encodeToString()
    }
}
