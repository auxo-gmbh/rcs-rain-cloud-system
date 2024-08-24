package master.thesis.raincloudsystem.communicatr.connections.remote

import master.thesis.raincloudsystem.communicatr.config.properties.CommunicatrProperties
import master.thesis.raincloudsystem.communicatr.connections.CommunicationLinkFactory
import master.thesis.raincloudsystem.communicatr.connections.OutgoingConnectionsManager
import master.thesis.raincloudsystem.communicatr.service.BlockListService
import master.thesis.raincloudsystem.shared.storage.Database
import org.springframework.context.annotation.Profile
import org.springframework.stereotype.Component
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.Semaphore

@Component
@Profile("remote")
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
        val port = communicatrProperties.port
        val ipRange = communicatrProperties.minRemoteIP..communicatrProperties.maxRemoteIP
        val includedIPs = communicatrProperties.includedIPs
        return ipRange
            .filter { includedIPs.isEmpty() || it in includedIPs }
            .map { communicatrProperties.remoteIPFormat.format(it) }
            .filterNot { it == communicatrProperties.localIP }
            .map { ip -> InetSocketAddress(ip, port) }
    }

    override fun tryAndConnect(remoteAddress: InetSocketAddress) {
        val socket = Socket()
        try {
            val lastIPPart = remoteAddress.hostName.substringAfterLast(".").toInt()
            if (isNewConnectionAndNotBlockListed(lastIPPart)) {
                socket.connect(remoteAddress)
                communicationLinkFactory.createCommunicationLink(socket)
            }
        } catch (_: IOException) {
        }
    }
}
