package master.thesis.raincloudsystem.communicatr.service

import master.thesis.raincloudsystem.coordinatr.config.properties.ACOProperties
import master.thesis.raincloudsystem.coordinatr.config.properties.GossipsProperties
import master.thesis.raincloudsystem.coordinatr.strategy.impl.ACOAlgorithm
import master.thesis.raincloudsystem.shared.utils.PheromonesTable
import master.thesis.raincloudsystem.shared.utils.RemotePort
import mu.KotlinLogging
import org.springframework.core.env.Environment
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class SelfActualizationService(
    private val acoProperties: ACOProperties,
    private val acoAlgorithm: ACOAlgorithm,
    private val gossipsProperties: GossipsProperties,
    private val gossipsQuality: ConcurrentHashMap<RemotePort, Int>,
    private val environment: Environment,
    private val avoidListService: AvoidListService
) {
    private val logger = KotlinLogging.logger {}

    @Scheduled(initialDelayString = "\${sa.initialDelayMs}", fixedRateString = "\${sa.fixedRateMs}")
    fun execute() {
        val removableEdges = getRemovableEdges()
        if (removableEdges.isNotEmpty()) {
            handleSelfActualization(removableEdges)
        }
    }

    fun getRemovableEdges() = when {
        "ACO" in environment.activeProfiles -> {
            val quality = acoAlgorithm.getPheromonesTables()
            val removableEdges = executeForACO(quality)
            logger.info { "Self-actualization: Calculated these edges $removableEdges to be removed in table $quality" }
            removableEdges
        }

        "Gossips" in environment.activeProfiles -> {
            val removableEdges = executeForGossips(gossipsQuality)
            logger.info { "Self-actualization: Calculated these edges $removableEdges to be removed $gossipsQuality" }
            removableEdges
        }

        else -> emptySet()
    }

    private fun handleSelfActualization(removableEdges: Set<RemotePort>) {
        avoidListService.addAllToPendingAvoidList(removableEdges)
    }

    private fun executeForACO(pheromonesTable: PheromonesTable): Set<RemotePort> {
        if (pheromonesTable.all { it.value.isEmpty() }) {
            return emptySet()
        }
        if (checkUnderThreshold(pheromonesTable)) {
            logger.info { "Threshold exceeded all edges to be removed: ${pheromonesTable.values.first().keys.toSet()}" }
            return pheromonesTable.values.first().keys.toSet()
        }
        return calculateLowerBoundOutliers(pheromonesTable)
    }

    private fun executeForGossips(countsPerNode: Map<RemotePort, Int>): Set<RemotePort> {
        if (countsPerNode.isEmpty()) {
            return emptySet()
        }
        if (checkUnderThreshold(countsPerNode)) {
            logger.info { "Threshold exceeded all edges to be removed: ${countsPerNode.keys}" }
            return countsPerNode.keys
        }
        val mappedCountsPerNode = countsPerNode.mapValues { item -> item.value.toDouble() }
        return calculateOutlier(mappedCountsPerNode).toSet()
    }

    private fun checkUnderThreshold(countsPerNode: Map<RemotePort, Int>) =
        countsPerNode.all { it.value < gossipsProperties.thresholdSA }

    private fun checkUnderThreshold(pheromonesTable: PheromonesTable) =
        pheromonesTable.all { (_, edges) ->
            edges.all { (_, pheromones) ->
                pheromones < acoProperties.thresholdSA
            }
        }

    private fun calculateLowerBoundOutliers(pheromonesTable: PheromonesTable): Set<RemotePort> {
        var remotePorts = pheromonesTable.values.first().keys.toSet()
        pheromonesTable.forEach { (taskType, edges) ->
            logger.info { "Calculating outliers for $taskType for $edges" }
            val removableCandidate = calculateOutlier(edges)
            remotePorts = remotePorts.intersect(removableCandidate.toSet())
        }

        return remotePorts
    }

    private fun calculateOutlier(edges: Map<RemotePort, Double>): List<RemotePort> {
        val sortedValues = edges.values.toList().sorted()
        if (sortedValues.size <= 1) {
            return emptyList()
        }
        val q2 = calculateMedian(sortedValues)
        val q1 = calculateQ1(sortedValues)
        val q3 = calculateQ3(sortedValues)
        val iqr = q3 - q1
        val lowerFence = q1 - 1.5 * iqr

        logger.info { "Self-actualization: Calculated lower fence: $lowerFence, q2: $q2, q1: $q1, q3: $q3, iqr: $iqr" }

        return edges.filter { (_, value) -> value < lowerFence }.keys.toList()
    }

    private fun calculateMedian(list: List<Double>) = list.let {
        if (it.size % 2 == 0) {
            (it[it.size / 2] + it[(it.size - 1) / 2]) / 2
        } else {
            it[it.size / 2]
        }
    }

    private fun calculateQ1(list: List<Double>): Double = list.let {
        val sublist = if (it.size % 2 == 0) {
            it.subList(0, it.size / 2)
        } else {
            it.subList(0, (it.size / 2) + 1)
        }
        return calculateMedian(sublist)
    }

    private fun calculateQ3(list: List<Double>): Double = list.let {
        val sublist = if (it.size % 2 == 0) {
            it.subList(it.size / 2, it.size)
        } else {
            it.subList((it.size / 2) + 1, it.size)
        }
        return calculateMedian(sublist)
    }
}
