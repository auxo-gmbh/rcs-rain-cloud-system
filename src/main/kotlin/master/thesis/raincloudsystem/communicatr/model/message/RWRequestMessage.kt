package master.thesis.raincloudsystem.communicatr.model.message

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.common.TaskMessage
import master.thesis.raincloudsystem.shared.utils.InstantSerializer
import java.time.Instant

@Serializable
data class RWRequestMessage(
    override var remotePort: Int,
    override var requestId: String,
    override var path: MutableList<Int>,
    override var taskMessage: TaskMessage,
    override var functionTime: Double?,
    @Serializable(InstantSerializer::class)
    override var deadline: Instant,
    @EncodeDefault
    override var messageType: MessageType = MessageType.RANDOM_WALKER_REQUEST
) : RequestMessage
