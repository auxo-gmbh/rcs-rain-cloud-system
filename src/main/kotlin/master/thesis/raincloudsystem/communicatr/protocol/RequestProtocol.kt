package master.thesis.raincloudsystem.communicatr.protocol
import master.thesis.raincloudsystem.communicatr.model.message.RequestMessage

interface RequestProtocol : Protocol {
    fun initialWrite(request: RequestMessage): String

    fun forwardWrite(request: RequestMessage): String
}
