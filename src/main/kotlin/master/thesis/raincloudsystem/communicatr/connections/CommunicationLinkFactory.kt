package master.thesis.raincloudsystem.communicatr.connections

import master.thesis.raincloudsystem.communicatr.config.properties.CommunicatrProperties
import master.thesis.raincloudsystem.communicatr.connections.links.CommunicationLink
import org.springframework.beans.factory.ObjectFactory
import org.springframework.stereotype.Component
import java.net.Socket

@Component
class CommunicationLinkFactory(
    private val communicatrProperties: CommunicatrProperties,
    private val beanFactory: ObjectFactory<CommunicationLink>
) {
    fun createCommunicationLink(socket: Socket) {
        val communicationLink = beanFactory.`object`
        communicationLink.socket = socket
        communicationLink.run()
        Thread.sleep(communicatrProperties.nodeDiscoveryInterval)
    }
}
