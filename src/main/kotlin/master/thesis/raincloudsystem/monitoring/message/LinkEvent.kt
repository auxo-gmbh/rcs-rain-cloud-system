package master.thesis.raincloudsystem.monitoring.message

import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.monitoring.enums.EventType

@Serializable
data class LinkEvent(
    override var eventType: EventType,
    var sourcePort: Int? = null,
    var targetPort: Int,
    var openedConnectionAt: Long? = null,
    var closedConnectionAt: Long? = null,
    var fromSelfActualization: Boolean? = null
) : Event
