package master.thesis.raincloudsystem.monitoring.message

import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.monitoring.enums.EventType

@Serializable
data class QueueEvent(
    override var eventType: EventType = EventType.QUEUE_EVENT,
    var sourcePort: Int? = null,
    var sentAt: Long,
    var queueSize: Int,
    var minQueueOccupation: Int?,
    var maxQueueOccupation: Int?,
    var averageQueueOccupation: Double,
    var standardDeviationQueueOccupation: Double,
    var varianceQueueOccupation: Double
) : Event
