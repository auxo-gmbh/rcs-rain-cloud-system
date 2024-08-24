package master.thesis.raincloudsystem.monitoring.message

import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.monitoring.enums.EventType

@Serializable
data class GossipsDiscoveryEvent(
    var requestId: String,
    var responses: List<GossipDiscoveryResponseEvent>,
    var sentAt: Long = System.currentTimeMillis(),
    var sourcePort: Int? = null,
    override var eventType: EventType = EventType.GOSSIPS_DISCOVERY_EVENT
) : Event
