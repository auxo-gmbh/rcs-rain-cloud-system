package master.thesis.raincloudsystem.communicatr.protocol

import master.thesis.raincloudsystem.communicatr.model.domain.InitialWriteMessage
import master.thesis.raincloudsystem.communicatr.model.message.ResponseMessage

interface ResponseProtocol : Protocol {
    fun initialWrite(message: InitialWriteMessage): String

    fun forwardWrite(response: ResponseMessage): String
}
