package master.thesis.raincloudsystem.communicatr.model.domain

import master.thesis.raincloudsystem.shared.utils.TaskType

data class GossipsDiscoveryInitialWriteMessage(
    override var requestId: String,
    override var path: List<Int>,
    var taskType: TaskType,
    var averageQueueRatio: Double,
    var averageResourcesRatio: Double
) : InitialWriteMessage(requestId, path)
