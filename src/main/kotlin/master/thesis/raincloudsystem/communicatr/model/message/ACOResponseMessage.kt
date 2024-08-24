package master.thesis.raincloudsystem.communicatr.model.message

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.common.TaskMessage

@Serializable
data class ACOResponseMessage(
    override var remotePort: Int,
    override var requestId: String,
    var taskMessage: TaskMessage,
    override var path: MutableList<Int>,
    var qualityPheromones: Double,
    @EncodeDefault
    override var messageType: MessageType = MessageType.ACO_RESPONSE
) : ResponseMessage
