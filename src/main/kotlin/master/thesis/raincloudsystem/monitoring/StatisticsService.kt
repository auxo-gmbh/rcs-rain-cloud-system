package master.thesis.raincloudsystem.monitoring

import master.thesis.raincloudsystem.monitoring.message.QueueEvent
import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.task.RCSTask
import master.thesis.raincloudsystem.shared.utils.toWholeMinute
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.LinkedBlockingDeque
import java.util.concurrent.Semaphore
import kotlin.math.pow
import kotlin.math.sqrt

@Component
class StatisticsService(
    private val rcsProperties: RCSProperties,
    private val queue: LinkedBlockingDeque<RCSTask>,
    private val monitoringService: MonitoringService
) {

    private val mutex = Semaphore(1)
    private val statistics = mutableListOf<Int>()
    private val persistedStatistics = mutableListOf<Int>()

    @Scheduled(cron = "\${stats.statisticsCron}")
    fun collectStatistics() {
        runMutuallyExclusive {
            statistics.add(queue.size)
            persistedStatistics.add(queue.size)
        }
    }

    @Scheduled(cron = "\${stats.queueCron}")
    fun monitoringQueue() {
        runMutuallyExclusive {
            val min = statistics.minOrNull()
            val max = statistics.maxOrNull()
            val average = if (statistics.size == 0) 0.0 else statistics.average()
            val variance = if (statistics.size == 0) 0.0 else calculateVariance(statistics, average)
            val standardDeviation = if (statistics.size == 0) 0.0 else calculateSD(variance)

            val queueEvent = QueueEvent(
                sentAt = System.currentTimeMillis().toWholeMinute(),
                queueSize = rcsProperties.queueSize,
                minQueueOccupation = min,
                maxQueueOccupation = max,
                averageQueueOccupation = average,
                standardDeviationQueueOccupation = standardDeviation,
                varianceQueueOccupation = variance
            )
            monitoringService.publishMessage(queueEvent)
            statistics.clear()
        }
    }

    fun getAveragePersistedQueueOccupation(): Double {
        return if (persistedStatistics.isNotEmpty()) persistedStatistics.average() else 0.0
    }

    private fun calculateVariance(statistics: List<Int>, average: Double): Double {
        return statistics
            .fold(0.0) { accumulator, next -> accumulator + (next - average).pow(2.0) }
            .let { it / statistics.size }
    }

    private fun calculateSD(variance: Double): Double {
        return sqrt(variance)
    }

    private fun runMutuallyExclusive(function: () -> Unit) {
        try {
            mutex.acquire()
            function.invoke()
        } finally {
            mutex.release()
        }
    }
}
