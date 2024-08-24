package master.thesis.raincloudsystem.coordinatr.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "gossips")
data class GossipsProperties(
    val spreadingTTLFactor: Double,
    var thresholdSA: Double
)
