package master.thesis.raincloudsystem.communicatr.model.domain

data class ACOInitialWriteMessage(
    override var requestId: String,
    override var path: List<Int>,
    var taskType: String,
    var qualityPheromones: Double
) : InitialWriteMessage(requestId, path)
