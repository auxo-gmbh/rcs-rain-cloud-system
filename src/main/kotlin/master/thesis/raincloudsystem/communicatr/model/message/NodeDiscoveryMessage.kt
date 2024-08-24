package master.thesis.raincloudsystem.communicatr.model.message

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.common.ResourcesMessage
import master.thesis.raincloudsystem.communicatr.model.message.common.TaskMessage

@Serializable
data class NodeDiscoveryMessage(
    override var remotePort: Int,
    val tasks: List<TaskMessage>,
    val resources: ResourcesMessage,
    @EncodeDefault
    override var messageType: MessageType = MessageType.NODE_DISCOVERY
) : Message
