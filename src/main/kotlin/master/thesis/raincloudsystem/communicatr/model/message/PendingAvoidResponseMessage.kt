package master.thesis.raincloudsystem.communicatr.model.message

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType

@Serializable
data class PendingAvoidResponseMessage(
    override var remotePort: Int,
    var isAccepted: Boolean,
    @EncodeDefault
    override var messageType: MessageType = MessageType.PENDING_AVOID_RESPONSE
) : Message
