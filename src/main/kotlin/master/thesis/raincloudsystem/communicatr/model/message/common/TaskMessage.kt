package master.thesis.raincloudsystem.communicatr.model.message.common

import kotlinx.serialization.Serializable

@Serializable
data class TaskMessage(val type: String)
