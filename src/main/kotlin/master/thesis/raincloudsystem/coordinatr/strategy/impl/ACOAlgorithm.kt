package master.thesis.raincloudsystem.coordinatr.strategy.impl

import master.thesis.raincloudsystem.communicatr.service.AvoidListService
import master.thesis.raincloudsystem.coordinatr.config.properties.ACOProperties
import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.model.domain.NodeProfile
import master.thesis.raincloudsystem.shared.model.domain.Resources
import master.thesis.raincloudsystem.shared.utils.Edges
import master.thesis.raincloudsystem.shared.utils.PheromonesTable
import master.thesis.raincloudsystem.shared.utils.QualityTable
import master.thesis.raincloudsystem.shared.utils.RemotePort
import master.thesis.raincloudsystem.shared.utils.StartUpMethod
import master.thesis.raincloudsystem.shared.utils.TaskType
import mu.KotlinLogging
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.random.Random

@Component
class ACOAlgorithm(
    private val acoProperties: ACOProperties,
    private val rcsProperties: RCSProperties,
    private val avoidListService: AvoidListService,
    private val environment: Environment
) {

    private val pheromonesTables = PheromonesTable()
    private val qualityTable = QualityTable()

    private val logger = KotlinLogging.logger {}

    @Scheduled(cron = "\${aco.evaporationFrequency}")
    fun evaporation() {
        if ("ACO" in environment.activeProfiles) {
            logger.debug { "Evaporation: pheromones table before $pheromonesTables" }
            pheromonesTables.values.forEach { edges ->
                edges.keys.forEach { remotePort ->
                    edges[remotePort] = (1 - acoProperties.rho) * edges.getOrDefault(remotePort, 0.0)
                }
            }
            logger.debug { "Evaporation: pheromones table after $pheromonesTables" }
        }
    }

    @StartUpMethod
    fun createPheromonesTable() {
        rcsProperties.supportedTasks.forEach { pheromonesTables[it] = ConcurrentHashMap() }
    }

    fun getPheromonesTables(): PheromonesTable = pheromonesTables

    fun createPheromonesTableForNewTaskType(taskType: TaskType): Edges {
        val copiedEdges = pheromonesTables.values.first()
        val initializedEdges = Edges(copiedEdges.mapValues { acoProperties.tau0 })
        pheromonesTables[taskType] = initializedEdges
        return pheromonesTables[taskType]!!
    }

    fun getEdgesByTaskType(taskType: TaskType): Edges? {
        return pheromonesTables[taskType]
    }

    private fun filterOutAvoidedEdges(edges: Edges): Edges {
        return Edges(edges.filterNot { (remotePort, _) -> avoidListService.isPartOfAvoidList(remotePort) })
    }

    fun updatePheromoneTables(
        localPort: RemotePort,
        remotePort: RemotePort,
        taskType: TaskType,
        path: List<Int>,
        qualityPheromones: Double
    ) {
        val pathLengthQuality = calculatePathLengthPheromones(localPort, path)
        val deltaPheromones = pathLengthQuality * qualityPheromones * 100
        val edges = pheromonesTables[taskType]!!

        pheromonesTables[taskType] = Edges(
            edges.mapValues { (entryRemotePort, pheromones) ->
                if (entryRemotePort == remotePort) {
                    val newPheromones = pheromones + deltaPheromones
                    logger.info { "Updated pheromones (delta: $deltaPheromones, quality: $qualityPheromones, path: $pathLengthQuality) for port $remotePort with task type $taskType from $pheromones to $newPheromones" }
                    return@mapValues newPheromones
                }
                return@mapValues pheromones
            }
        )
    }

    fun calculateQualityPheromones(
        averageQueueOccupation: Double,
        numberOfCommunicationLinks: Int,
        resources: Resources
    ): Double {
        var queueOccupationQuality = 1 - (averageQueueOccupation / rcsProperties.queueSize)

        if (queueOccupationQuality <= 0.0) {
            queueOccupationQuality = 0.0
        }

        val resourcesList = listOf(
            rcsProperties.communication,
            rcsProperties.computation,
            rcsProperties.storage
        )
        val resourcesQuality = (1.0 / resourcesList.average())

        logger.info { "Calculated quality pheromones based on $averageQueueOccupation for queueOccupationQuality: $queueOccupationQuality - resourcesQuality: $resourcesQuality" }
        return queueOccupationQuality * resourcesQuality
    }

    fun deleteEdgeInfo(remotePort: RemotePort) {
        pheromonesTables.values.forEach { edges ->
            edges.remove(remotePort)
        }
        qualityTable.remove(remotePort)
    }

    fun initializePheromonesOfConnectedEdges(remoteNodeProfile: NodeProfile) {
        saveQualityForConnectedEdge(remoteNodeProfile)
        createTaskTypesForConnectedEdge(remoteNodeProfile)
        calculatePheromonesForConnectedEdge(remoteNodeProfile)
    }

    private fun saveQualityForConnectedEdge(remoteNodeProfile: NodeProfile) {
        qualityTable[remoteNodeProfile.remotePort] = remoteNodeProfile.taskTypes.map { task -> task.type }
    }

    private fun createTaskTypesForConnectedEdge(remoteNodeProfile: NodeProfile) {
        remoteNodeProfile.taskTypes.forEach { taskType ->
            if (!pheromonesTables.containsKey(taskType.type)) {
                pheromonesTables[taskType.type] = Edges()
            }
        }
    }

    private fun calculatePheromonesForConnectedEdge(remoteNodeProfile: NodeProfile) {
        pheromonesTables.forEach { (taskType, edges) ->
            if (remoteNodeProfile.taskTypes.any { it.type == taskType }) {
                edges[remoteNodeProfile.remotePort] = acoProperties.tau0Enhanced
            } else {
                edges[remoteNodeProfile.remotePort] = acoProperties.tau0
            }
        }
    }

    private fun calculatePathLengthPheromones(
        remotePort: RemotePort,
        path: List<RemotePort>
    ): Double {
        val hopsToDestination = path.size - path.indexOf(remotePort)
        return (1.0 / hopsToDestination)
    }

    fun getNextCommunicationLinkPort(taskType: TaskType, path: List<Int>): RemotePort {
        val edges = getEdgesByTaskType(taskType) ?: createPheromonesTableForNewTaskType(taskType)
        val notAvoidedEdges = filterOutAvoidedEdges(edges)
        val edgesWithoutPotentialCircles = notAvoidedEdges.filter { edge -> !path.contains(edge.key) }

        if (edgesWithoutPotentialCircles.isEmpty()) {
            return -1
        }

        val probabilityArray = calculateProbabilitiesForEdges(Edges(edgesWithoutPotentialCircles), taskType)
        val cumulativeProbabilityMap = calculateCumulativeProbabilityMap(probabilityArray)
        return selectRandomPortFromProbabilityMap(cumulativeProbabilityMap)
    }

    private fun calculateProbabilitiesForEdges(edges: Edges, taskType: TaskType): Map<RemotePort, Double> {
        val nominators = HashMap<RemotePort, Double>()
        var discriminator = 0.0
        edges.forEach { edge ->
            val nominator = calculateProbabilityForEdge(edge.key, edge.value, taskType)
            discriminator += nominator
            nominators[edge.key] = nominator
        }
        return nominators.mapValues { (_, nominator) -> nominator / discriminator }
    }

    private fun calculateProbabilityForEdge(remotePort: RemotePort, tau: Double, taskType: TaskType): Double {
        val eta = calculateEtaByRemotePort(remotePort, taskType)
        return tau.pow(acoProperties.alpha) * eta.pow(acoProperties.beta)
    }

    private fun calculateEtaByRemotePort(remotePort: RemotePort, taskType: TaskType): Double {
        val remoteSupportedTasks = qualityTable.getOrDefault(remotePort, emptyList())
        return if (remoteSupportedTasks.contains(taskType)) acoProperties.tau0Enhanced else acoProperties.tau0
    }

    private fun calculateCumulativeProbabilityMap(
        probabilityMap: Map<RemotePort, Double>
    ): Map<RemotePort, Double> {
        var sum = 0.0
        return probabilityMap.mapValues { (_, probability) ->
            sum += probability
            return@mapValues sum
        }
    }

    private fun selectRandomPortFromProbabilityMap(
        cumulativeProbabilityMap: Map<RemotePort, Double>
    ): RemotePort {
        val randomNumber = Random.nextDouble(0.0, 1.0)

        val iterator = cumulativeProbabilityMap.entries.iterator()
        var prev = 0.0
        while (iterator.hasNext()) {
            val nextEntry = iterator.next()
            val next = nextEntry.value
            if (prev < randomNumber && randomNumber <= next) {
                val chosenPort = nextEntry.key
                logger.info { "ACO calculated random number $randomNumber for cumulative map $cumulativeProbabilityMap and chose $chosenPort" }
                return chosenPort
            }
            prev = next
        }
        return cumulativeProbabilityMap.keys.first()
    }
}
