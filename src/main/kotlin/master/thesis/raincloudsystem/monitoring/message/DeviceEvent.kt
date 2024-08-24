package master.thesis.raincloudsystem.monitoring.message

import kotlinx.serialization.Serializable
import master.thesis.raincloudsystem.monitoring.enums.EventType

@Serializable
data class DeviceEvent(
    override var eventType: EventType = EventType.DEVICE_EVENT,
    var sourcePort: Int,
    var sentAt: Long = System.currentTimeMillis(),
    var taskTypes: List<String>
) : Event
