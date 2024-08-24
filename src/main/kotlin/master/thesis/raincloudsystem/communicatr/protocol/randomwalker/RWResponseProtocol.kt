package master.thesis.raincloudsystem.communicatr.protocol.randomwalker

import master.thesis.raincloudsystem.communicatr.model.domain.InitialWriteMessage
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import master.thesis.raincloudsystem.communicatr.model.message.RWResponseMessage
import master.thesis.raincloudsystem.communicatr.model.message.ResponseMessage
import master.thesis.raincloudsystem.communicatr.protocol.ResponseProtocol
import master.thesis.raincloudsystem.coordinatr.strategy.impl.RandomWalkerStrategy
import master.thesis.raincloudsystem.shared.model.domain.NodeProfile
import master.thesis.raincloudsystem.shared.utils.decodeFromString
import master.thesis.raincloudsystem.shared.utils.encodeToString
import org.springframework.context.annotation.Lazy
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component

@Component
@Profile("Random-Walker")
class RWResponseProtocol(
    @Lazy
    private val randomWalkerStrategy: RandomWalkerStrategy,
    private val localNodeProfile: NodeProfile
) : ResponseProtocol {

    override var type: MessageType = MessageType.RANDOM_WALKER_RESPONSE

    override fun initialWrite(message: InitialWriteMessage): String {
        val response = mapToResponseMessage(message.requestId, message.path)
        return response.encodeToString()
    }

    override fun forwardWrite(response: ResponseMessage): String {
        val rwResponseMessage = response as RWResponseMessage
        return rwResponseMessage.encodeToString()
    }

    override fun read(request: String) {
        val rwResponseMessage: RWResponseMessage = request.decodeFromString()
        randomWalkerStrategy.handleResponse(rwResponseMessage)
    }

    private fun mapToResponseMessage(requestId: String, path: List<Int>): RWResponseMessage {
        val remotePort = localNodeProfile.remotePort
        return RWResponseMessage(remotePort, requestId, path.toMutableList())
    }
}
