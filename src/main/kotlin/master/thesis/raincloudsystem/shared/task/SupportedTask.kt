package master.thesis.raincloudsystem.shared.task

interface SupportedTask : RCSTask {
    var type: String
    var timeToLive: Long // in seconds
    var functionTime: Double?
}
