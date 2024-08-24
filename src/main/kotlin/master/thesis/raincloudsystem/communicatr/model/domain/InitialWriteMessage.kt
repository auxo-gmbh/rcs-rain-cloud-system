package master.thesis.raincloudsystem.communicatr.model.domain

open class InitialWriteMessage(
    open var requestId: String,
    open var path: List<Int>
)
