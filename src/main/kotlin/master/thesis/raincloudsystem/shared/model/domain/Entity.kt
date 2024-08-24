package master.thesis.raincloudsystem.shared.model.domain

import master.thesis.raincloudsystem.communicatr.connections.links.CommunicationLink

data class Entity(
    val nodeProfile: NodeProfile,
    val communicationLink: CommunicationLink
)
