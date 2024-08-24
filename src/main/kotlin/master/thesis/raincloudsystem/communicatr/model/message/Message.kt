package master.thesis.raincloudsystem.communicatr.model.message

import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType

@Serializable
sealed interface Message {
    var remotePort: Int
    var messageType: MessageType
}
