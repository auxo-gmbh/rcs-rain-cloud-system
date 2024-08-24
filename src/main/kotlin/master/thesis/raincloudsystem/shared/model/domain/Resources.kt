package master.thesis.raincloudsystem.shared.model.domain

import kotlinx.serialization.Serializable

@Serializable
data class Resources(
    val computation: Long,
    val communication: Long,
    val storage: Long
)
