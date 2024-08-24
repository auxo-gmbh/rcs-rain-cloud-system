package master.thesis.raincloudsystem.monitoring.message

import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.monitoring.enums.DetailsType
import master.thesis.raincloudsystem.monitoring.enums.EventType
import master.thesis.raincloudsystem.shared.utils.RemotePort

@Serializable
data class MessagingEvent(
    override var eventType: EventType = EventType.MESSAGING_EVENT,
    var requestId: String? = null,
    var sentAt: Long = System.currentTimeMillis(),
    var taskType: String? = null,
    var path: List<Int>? = null,
    var details: DetailsType,
    var sourcePort: Int? = null,
    var targetPort: Int,
    var pheromonesEdges: Map<RemotePort, Double>? = null,
    var qualityPheromones: Double? = null,
    var functionTime: Double? = null
) : Event
