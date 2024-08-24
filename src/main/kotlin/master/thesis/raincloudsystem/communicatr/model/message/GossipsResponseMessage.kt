package master.thesis.raincloudsystem.communicatr.model.message

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType

@Serializable
data class GossipsResponseMessage(
    override var remotePort: Int,
    override var requestId: String,
    override var path: MutableList<Int>,
    @EncodeDefault
    override var messageType: MessageType = MessageType.GOSSIPS_RESPONSE
) : ResponseMessage
