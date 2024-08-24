package master.thesis.raincloudsystem.communicatr.model.message

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType.PENDING_AVOID_REQUEST

@Serializable
data class PendingAvoidRequestMessage(
    override var remotePort: Int,
    @EncodeDefault
    override var messageType: MessageType = PENDING_AVOID_REQUEST
) : Message
