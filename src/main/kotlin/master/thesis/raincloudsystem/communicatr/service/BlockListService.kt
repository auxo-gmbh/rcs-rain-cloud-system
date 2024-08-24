package master.thesis.raincloudsystem.communicatr.service

import master.thesis.raincloudsystem.communicatr.config.properties.SelfActualizationProperties
import master.thesis.raincloudsystem.shared.exception.NoCommunicationLinkException
import master.thesis.raincloudsystem.shared.storage.Database
import master.thesis.raincloudsystem.shared.utils.RemotePort
import mu.KotlinLogging
import org.springframework.context.annotation.Lazy
import org.springframework.scheduling.TaskScheduler
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.temporal.ChronoUnit

@Component
class BlockListService(
    @Lazy
    private val database: Database,
    private val taskScheduler: TaskScheduler,
    private val selfActualizationProperties: SelfActualizationProperties
) {

    private val logger = KotlinLogging.logger {}
    private val blockList = mutableSetOf<RemotePort>()

    fun addToBlockList(remotePort: RemotePort) {
        addRemotePortToBlockList(remotePort)
        scheduleUnblockingRemotePort(remotePort)
        disconnectFromRemotePort(remotePort)
    }

    fun isPartOfBlocklist(remotePort: RemotePort) = blockList.contains(remotePort)

    private fun addRemotePortToBlockList(remotePort: RemotePort) {
        blockList.add(remotePort)
        logger.info { "Added $remotePort to block list" }
    }

    private fun scheduleUnblockingRemotePort(remotePort: RemotePort) {
        taskScheduler.schedule(
            {
                blockList.remove(remotePort)
                logger.info { "Removed $remotePort from blocklist because block list minutes passed" }
            },
            Instant.now().plus(selfActualizationProperties.blockListMinutes, ChronoUnit.MINUTES)
        )
    }

    private fun disconnectFromRemotePort(remotePort: RemotePort) {
        logger.info { "Disconnecting $remotePort from block list" }
        try {
            val communicationLink = database.getCommunicationLinkByPort(remotePort)
            communicationLink.shutdown()
        } catch (_: NoCommunicationLinkException) {
            logger.info { "Tried to disconnect from $remotePort but communication link not found" }
        }
    }
}
