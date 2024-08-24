package master.thesis.raincloudsystem.monitoring.message

import master.thesis.raincloudsystem.monitoring.enums.EventType

sealed interface Event {
    var eventType: EventType
}
