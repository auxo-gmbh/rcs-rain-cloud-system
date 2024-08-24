package master.thesis.raincloudsystem.communicatr.connections.links

import master.thesis.raincloudsystem.communicatr.protocol.ProtocolHandler
import master.thesis.raincloudsystem.communicatr.protocol.nodediscovery.NodeDiscoveryProtocol
import master.thesis.raincloudsystem.communicatr.service.BlockListService
import master.thesis.raincloudsystem.shared.storage.Database
import master.thesis.raincloudsystem.shared.utils.RemotePort
import master.thesis.raincloudsystem.shared.utils.closeIfNotAlreadyClosed
import mu.KotlinLogging
import org.springframework.beans.factory.config.ConfigurableBeanFactory
import org.springframework.context.annotation.Scope
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Component
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.io.UncheckedIOException
import java.net.Socket
import java.net.SocketException
import javax.annotation.PreDestroy

@Component
@Scope(ConfigurableBeanFactory.SCOPE_PROTOTYPE)
class CommunicationLink(
    private var database: Database,
    private var blockListService: BlockListService,
    private var protocolHandler: ProtocolHandler,
    private var nodeDiscoveryProtocol: NodeDiscoveryProtocol
) {

    private val logger = KotlinLogging.logger {}

    lateinit var socket: Socket

    private var isRunning = true
    private lateinit var writer: PrintWriter
    private lateinit var reader: BufferedReader

    @Async
    fun run() {
        writer = PrintWriter(socket.outputStream, true)
        reader = BufferedReader(InputStreamReader(socket.inputStream))
        write(nodeDiscoveryProtocol.write())
        read()
    }

    @Async
    fun write(message: String) {
        writer.println(message)
    }

    private fun read() {
        try {
            var request = reader.readLine()
            val initialMessage = nodeDiscoveryProtocol.readInitialMessage(request)

            if (isAlreadyConnectedOrBlockListed(initialMessage.remotePort)) {
                return
            }

            database.save(initialMessage, this)

            request = reader.readLine()
            while (request != null) {
                protocolHandler.read(request)
                request = reader.readLine()
            }
        } catch (ex: SocketException) {
            if (isRunning) {
                throw UncheckedIOException(ex)
            }
        } finally {
            shutdown()
        }
    }

    private fun isAlreadyConnectedOrBlockListed(remotePort: RemotePort) =
        database.isAlreadyConnectedTo(remotePort) || blockListService.isPartOfBlocklist(remotePort)

    @PreDestroy
    fun shutdown() {
        if (isRunning) {
            logger.info { "CL shutting down" }
            isRunning = false
            database.deleteByCommunicationLink(this)
            socket.closeIfNotAlreadyClosed()
        }
    }
}
