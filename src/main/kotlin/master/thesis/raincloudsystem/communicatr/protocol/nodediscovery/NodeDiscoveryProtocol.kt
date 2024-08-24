package master.thesis.raincloudsystem.communicatr.protocol.nodediscovery

import master.thesis.raincloudsystem.communicatr.model.message.NodeDiscoveryMessage
import master.thesis.raincloudsystem.communicatr.model.message.common.ResourcesMessage
import master.thesis.raincloudsystem.communicatr.model.message.common.TaskMessage
import master.thesis.raincloudsystem.shared.model.domain.NodeProfile
import master.thesis.raincloudsystem.shared.utils.decodeFromString
import master.thesis.raincloudsystem.shared.utils.encodeToString
import org.springframework.stereotype.Component

@Component
class NodeDiscoveryProtocol(private val localNodeProfile: NodeProfile) {

    fun readInitialMessage(request: String): NodeDiscoveryMessage {
        return request.decodeFromString()
    }

    fun write(): String {
        val nodeDiscoveryMessage = mapToNodeDiscoveryMessage(localNodeProfile)
        return nodeDiscoveryMessage.encodeToString()
    }

    private fun mapToNodeDiscoveryMessage(nodeProfile: NodeProfile): NodeDiscoveryMessage {
        val (remotePort, tasks, resources) = nodeProfile
        val taskMessage = tasks.map { TaskMessage(it.type) }
        val resourcesMessage = ResourcesMessage(resources.computation, resources.communication, resources.storage)
        return NodeDiscoveryMessage(remotePort, taskMessage, resourcesMessage)
    }
}
