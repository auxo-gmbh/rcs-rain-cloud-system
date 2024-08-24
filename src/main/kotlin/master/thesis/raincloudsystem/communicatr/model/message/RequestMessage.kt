package master.thesis.raincloudsystem.communicatr.model.message

import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.communicatr.model.message.common.TaskMessage
import java.time.Instant

@Serializable
sealed interface RequestMessage : Message {
    var requestId: String
    var path: MutableList<Int>
    var taskMessage: TaskMessage
    var deadline: Instant
    var functionTime: Double?
}
