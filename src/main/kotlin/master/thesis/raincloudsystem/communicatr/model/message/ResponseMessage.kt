package master.thesis.raincloudsystem.communicatr.model.message

import kotlinx.serialization.Serializable

@Serializable
sealed interface ResponseMessage : Message {
    var requestId: String
    var path: MutableList<Int>
}
