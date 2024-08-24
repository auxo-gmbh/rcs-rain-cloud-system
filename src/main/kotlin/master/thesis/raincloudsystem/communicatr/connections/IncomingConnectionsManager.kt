package master.thesis.raincloudsystem.communicatr.connections

import master.thesis.raincloudsystem.shared.utils.StartUpMethod
import master.thesis.raincloudsystem.shared.utils.closeIfNotAlreadyClosed
import master.thesis.raincloudsystem.shared.utils.whileIsTrue
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.io.IOException
import java.io.UncheckedIOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Semaphore
import javax.annotation.PreDestroy

@Component
class IncomingConnectionsManager(
    private val serverSocket: ServerSocket,
    private val connectionMutex: Semaphore,
    private val communicationLinkFactory: CommunicationLinkFactory
) {

    private val logger = KotlinLogging.logger {}
    private var isRunning = true

    @StartUpMethod
    fun run() {
        isRunning.whileIsTrue {
            var socket = Socket()
            try {
                socket = serverSocket.accept()
                connectionMutex.acquire()
                communicationLinkFactory.createCommunicationLink(socket)
            } catch (exception: IOException) {
                if (isRunning) {
                    socket.closeIfNotAlreadyClosed()
                    throw UncheckedIOException(exception)
                }
            } finally {
                connectionMutex.release()
            }
        }
    }

    @PreDestroy
    fun shutdown() {
        logger.info { "ICM is shutting down" }
        isRunning = false
        serverSocket.close()
    }
}
