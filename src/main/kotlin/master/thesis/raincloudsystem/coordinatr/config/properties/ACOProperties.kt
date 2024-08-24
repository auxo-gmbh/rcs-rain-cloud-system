package master.thesis.raincloudsystem.coordinatr.config.properties

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.ConstructorBinding

@ConstructorBinding
@ConfigurationProperties(prefix = "aco")
data class ACOProperties(
    var tau0: Double,
    var tau0Enhanced: Double,
    var alpha: Int,
    var beta: Int,
    var rho: Double,
    var occupationFactor: Int,
    var communicationLinksFactor: Double,
    var resourcesFactor: Int,
    var pathFactor: Int,
    var thresholdSA: Double,
    var evaporationFrequency: String
)
