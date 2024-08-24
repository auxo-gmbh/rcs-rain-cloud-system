package master.thesis.raincloudsystem.communicatr.protocol.aco

import master.thesis.raincloudsystem.communicatr.model.domain.ACOInitialWriteMessage
import master.thesis.raincloudsystem.communicatr.model.domain.InitialWriteMessage
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.ACOResponseMessage
import master.thesis.raincloudsystem.communicatr.model.message.ResponseMessage
import master.thesis.raincloudsystem.communicatr.model.message.common.TaskMessage
import master.thesis.raincloudsystem.communicatr.protocol.ResponseProtocol
import master.thesis.raincloudsystem.coordinatr.strategy.Strategy
import master.thesis.raincloudsystem.shared.model.domain.NodeProfile
import master.thesis.raincloudsystem.shared.utils.decodeFromString
import master.thesis.raincloudsystem.shared.utils.encodeToString
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("ACO")
class ACOResponseProtocol(
    @Lazy
    private val acoStrategy: Strategy,
    private val localNodeProfile: NodeProfile
) : ResponseProtocol {

    override var type: MessageType = MessageType.ACO_RESPONSE

    override fun read(request: String) {
        val acoResponseMessage: ACOResponseMessage = request.decodeFromString()
        acoStrategy.handleResponse(acoResponseMessage)
    }

    override fun forwardWrite(response: ResponseMessage): String {
        val acoResponseMessage = response as ACOResponseMessage
        return acoResponseMessage.encodeToString()
    }

    override fun initialWrite(message: InitialWriteMessage): String {
        val acoInitialWriteMessage = message as ACOInitialWriteMessage
        val response = mapToResponseMessage(acoInitialWriteMessage)
        return response.encodeToString()
    }

    private fun mapToResponseMessage(acoInitialWriteMessage: ACOInitialWriteMessage): ACOResponseMessage {
        val remotePort = localNodeProfile.remotePort
        return ACOResponseMessage(
            remotePort,
            acoInitialWriteMessage.requestId,
            TaskMessage(acoInitialWriteMessage.taskType),
            acoInitialWriteMessage.path.toMutableList(),
            acoInitialWriteMessage.qualityPheromones
        )
    }
}
