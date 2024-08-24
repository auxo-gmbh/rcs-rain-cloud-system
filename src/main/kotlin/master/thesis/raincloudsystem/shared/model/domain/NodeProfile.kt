package master.thesis.raincloudsystem.shared.model.domain

data class NodeProfile(
    val remotePort: Int,
    val taskTypes: List<Task>,
    val resources: Resources
)
