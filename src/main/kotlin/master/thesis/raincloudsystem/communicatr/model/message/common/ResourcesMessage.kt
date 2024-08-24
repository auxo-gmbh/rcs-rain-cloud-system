package master.thesis.raincloudsystem.communicatr.model.message.common

import kotlinx.serialization.Serializable

@Serializable
data class ResourcesMessage(
    val computation: Long,
    val communication: Long,
    val storage: Long
)
