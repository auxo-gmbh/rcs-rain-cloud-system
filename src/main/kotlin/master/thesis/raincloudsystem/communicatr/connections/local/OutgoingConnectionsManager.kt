package master.thesis.raincloudsystem.communicatr.connections.local

import master.thesis.raincloudsystem.communicatr.config.properties.CommunicatrProperties
import master.thesis.raincloudsystem.communicatr.connections.CommunicationLinkFactory
import master.thesis.raincloudsystem.communicatr.connections.OutgoingConnectionsManager
import master.thesis.raincloudsystem.communicatr.service.BlockListService
import master.thesis.raincloudsystem.shared.storage.Database
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Semaphore

@Component
@Profile("!remote")
class OutgoingConnectionsManager(
    private val communicatrProperties: CommunicatrProperties,
    private val communicationLinkFactory: CommunicationLinkFactory,
    connectionMutex: Semaphore,
    database: Database,
    blockListService: BlockListService
) : OutgoingConnectionsManager(
    communicatrProperties,
    connectionMutex,
    database,
    blockListService
) {

    override fun generateAddresses(): List<InetSocketAddress> {
        val host = InetAddress.getLocalHost()
        val ports = communicatrProperties.minRemotePort..communicatrProperties.maxRemotePort
        return ports.filter { it != communicatrProperties.localPort }.map { port ->
            InetSocketAddress(host, port)
        }
    }

    override fun tryAndConnect(remoteAddress: InetSocketAddress) {
        val socket = Socket()
        try {
            if (isNewConnectionAndNotBlockListed(remoteAddress.port)) {
                socket.connect(remoteAddress)
                communicationLinkFactory.createCommunicationLink(socket)
            }
        } catch (_: IOException) {
        }
    }
}
