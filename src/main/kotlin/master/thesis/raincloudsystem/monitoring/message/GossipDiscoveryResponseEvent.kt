package master.thesis.raincloudsystem.monitoring.message

import kotlinx.serialization.Serializable

@Serializable
data class GossipDiscoveryResponseEvent(
    var path: MutableList<Int>,
    var averageQueueRatio: Double,
    var averageResourcesRatio: Double
)
