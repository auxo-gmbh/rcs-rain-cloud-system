package master.thesis.raincloudsystem.communicatr.model.message

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.common.TaskMessage

@Serializable
data class GossipsDiscoveryResponseMessage(
    override var remotePort: Int,
    override var requestId: String,
    var taskMessage: TaskMessage,
    override var path: MutableList<Int>,
    var averageQueueRatio: Double,
    var averageResourcesRatio: Double,
    @EncodeDefault
    override var messageType: MessageType = MessageType.GOSSIPS_DISCOVERY_RESPONSE
) : ResponseMessage
