package master.thesis.raincloudsystem.communicatr.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "comm")
data class CommunicatrProperties(
    var localPort: Int,
    var localIP: String,
    var minConnections: Int,
    var minRemotePort: Int,
    var maxRemotePort: Int,
    var nodeDiscoveryInterval: Long,
    var port: Int,
    var minRemoteIP: Int,
    var maxRemoteIP: Int,
    var remoteIPFormat: String,
    var includedIPs: List<Int>
)
