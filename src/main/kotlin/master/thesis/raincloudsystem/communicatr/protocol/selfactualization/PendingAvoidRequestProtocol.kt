package master.thesis.raincloudsystem.communicatr.protocol.selfactualization

import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.PendingAvoidRequestMessage
import master.thesis.raincloudsystem.communicatr.protocol.Protocol
import master.thesis.raincloudsystem.communicatr.service.AvoidListService
import master.thesis.raincloudsystem.shared.utils.decodeFromString
import master.thesis.raincloudsystem.shared.utils.encodeToString
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class PendingAvoidRequestProtocol(
    @Lazy
    private val avoidListService: AvoidListService
) : Protocol {

    override var type = MessageType.PENDING_AVOID_REQUEST

    override fun read(request: String) {
        val requestMessage: PendingAvoidRequestMessage = request.decodeFromString()
        avoidListService.handleRequest(requestMessage)
    }

    fun write(request: PendingAvoidRequestMessage): String {
        return request.encodeToString()
    }
}
