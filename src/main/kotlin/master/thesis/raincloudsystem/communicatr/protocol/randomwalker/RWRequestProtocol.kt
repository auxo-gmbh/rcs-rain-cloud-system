package master.thesis.raincloudsystem.communicatr.protocol.randomwalker

import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.RWRequestMessage
import master.thesis.raincloudsystem.communicatr.model.message.RequestMessage
import master.thesis.raincloudsystem.communicatr.protocol.RequestProtocol
import master.thesis.raincloudsystem.coordinatr.strategy.Strategy
import master.thesis.raincloudsystem.shared.utils.decodeFromString
import master.thesis.raincloudsystem.shared.utils.encodeToString
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("Random-Walker")
class RWRequestProtocol(
    @Lazy private val randomWalkerStrategy: Strategy
) : RequestProtocol {

    override var type = MessageType.RANDOM_WALKER_REQUEST

    override fun read(request: String) {
        val rwRequestMessage: RWRequestMessage = request.decodeFromString()
        randomWalkerStrategy.handleRequest(rwRequestMessage)
    }

    override fun forwardWrite(request: RequestMessage): String {
        val rwRequestMessage = request as RWRequestMessage
        return rwRequestMessage.encodeToString()
    }

    override fun initialWrite(request: RequestMessage): String {
        val rwRequestMessage = request as RWRequestMessage
        return rwRequestMessage.encodeToString()
    }
}
