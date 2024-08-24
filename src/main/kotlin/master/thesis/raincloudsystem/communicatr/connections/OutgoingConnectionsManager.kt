package master.thesis.raincloudsystem.communicatr.connections

import master.thesis.raincloudsystem.communicatr.config.properties.CommunicatrProperties
import master.thesis.raincloudsystem.communicatr.service.BlockListService
import master.thesis.raincloudsystem.shared.storage.Database
import master.thesis.raincloudsystem.shared.utils.RemotePort
import master.thesis.raincloudsystem.shared.utils.StartUpMethod
import master.thesis.raincloudsystem.shared.utils.whileIsTrue
import mu.KotlinLogging
import org.springframework.stereotype.Component
import java.net.InetSocketAddress
import java.util.concurrent.Semaphore
import javax.annotation.PreDestroy

@Component
abstract class OutgoingConnectionsManager(
    private val communicatrProperties: CommunicatrProperties,
    private val connectionMutex: Semaphore,
    private val database: Database,
    private val blockListService: BlockListService
) {

    private val logger = KotlinLogging.logger {}
    private var isRunning = true

    @StartUpMethod
    fun run() {
        isRunning.whileIsTrue {
            generateAddresses()
                .shuffled()
                .forEach {
                    try {
                        Thread.sleep(50L)
                        connectionMutex.acquire()
                        if (database.getNumberOfCommunicationLinks() < communicatrProperties.minConnections) {
                            tryAndConnect(it)
                            if (!isRunning) {
                                return@forEach
                            }
                        }
                    } finally {
                        connectionMutex.release()
                    }
                }
        }
    }

    @PreDestroy
    fun shutdown() {
        logger.info { "OCM is shutting down" }
        isRunning = false
    }

    abstract fun generateAddresses(): List<InetSocketAddress>

    abstract fun tryAndConnect(remoteAddress: InetSocketAddress)

    protected fun isNewConnectionAndNotBlockListed(remotePort: RemotePort) =
        !database.isAlreadyConnectedTo(remotePort) && !blockListService.isPartOfBlocklist(remotePort)
}
