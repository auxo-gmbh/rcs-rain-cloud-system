package master.thesis.raincloudsystem.communicatr.protocol
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType

interface Protocol {
    var type: MessageType

    fun read(request: String)
}
