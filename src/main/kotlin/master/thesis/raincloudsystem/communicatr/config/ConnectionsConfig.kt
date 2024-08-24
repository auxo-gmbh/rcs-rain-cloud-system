package master.thesis.raincloudsystem.communicatr.config

import master.thesis.raincloudsystem.communicatr.config.properties.CommunicatrProperties
import mu.KotlinLogging
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import java.io.IOException
import java.io.UncheckedIOException
import java.net.ServerSocket
import java.util.concurrent.Semaphore

@Configuration
class ConnectionsConfig(
    private val communicatrProperties: CommunicatrProperties
) {

    private val logger = KotlinLogging.logger {}

    @Bean
    @Profile("!remote")
    fun serverSocket(): ServerSocket {
        try {
            return ServerSocket(communicatrProperties.localPort)
        } catch (exception: IOException) {
            logger.error { "Exception occurred while creating server socket: ${exception.message}" }
            throw UncheckedIOException(exception)
        }
    }

    @Bean
    @Profile("remote")
    fun remoteServerSocket(): ServerSocket {
        try {
            return ServerSocket(communicatrProperties.port)
        } catch (exception: IOException) {
            logger.error { "Exception occurred while creating server socket: ${exception.message}" }
            throw UncheckedIOException(exception)
        }
    }

    @Bean
    fun connectionMutex(): Semaphore {
        return Semaphore(1)
    }
}
