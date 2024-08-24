package master.thesis.raincloudsystem.communicatr.protocol.selfactualization

import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.PendingAvoidResponseMessage
import master.thesis.raincloudsystem.communicatr.protocol.Protocol
import master.thesis.raincloudsystem.communicatr.service.AvoidListService
import master.thesis.raincloudsystem.shared.utils.decodeFromString
import master.thesis.raincloudsystem.shared.utils.encodeToString
import org.springframework.context.annotation.Lazy
import org.springframework.stereotype.Component

@Component
class PendingAvoidResponseProtocol(
    @Lazy
    private val avoidListService: AvoidListService
) : Protocol {

    override var type = MessageType.PENDING_AVOID_RESPONSE

    override fun read(request: String) {
        val responseMessage: PendingAvoidResponseMessage = request.decodeFromString()
        avoidListService.handleResponse(responseMessage)
    }

    fun write(response: PendingAvoidResponseMessage): String {
        return response.encodeToString()
    }
}
