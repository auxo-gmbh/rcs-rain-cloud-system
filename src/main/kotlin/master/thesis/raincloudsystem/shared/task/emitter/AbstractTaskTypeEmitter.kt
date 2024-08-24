package master.thesis.raincloudsystem.shared.task.emitter

import master.thesis.raincloudsystem.coordinatr.strategy.Strategy
import master.thesis.raincloudsystem.shared.config.properties.RCSProperties
import master.thesis.raincloudsystem.shared.task.Emitter
import master.thesis.raincloudsystem.shared.task.SupportedTask
import master.thesis.raincloudsystem.shared.utils.FileLocation
import master.thesis.raincloudsystem.shared.utils.StartUpMethod
import master.thesis.raincloudsystem.shared.utils.TaskType
import master.thesis.raincloudsystem.shared.utils.getReaderOfFile
import org.springframework.stereotype.Component
import java.io.BufferedReader
import javax.annotation.PreDestroy

@Component
abstract class AbstractTaskTypeEmitter(
    private val rcsProperties: RCSProperties,
    private val strategy: Strategy
) : Emitter {

    private var isRunning = true
    private var lastEmitAt = 0L

    abstract var taskType: TaskType
    abstract var emitterFileLocation: FileLocation
    abstract var functionExecutionFileLocation: FileLocation

    lateinit var functionExecutionReader: BufferedReader

    @StartUpMethod
    override fun emit() {
        if (taskType in rcsProperties.supportedTasks) {
            functionExecutionReader = functionExecutionFileLocation.getReaderOfFile()
            skipCsvHeader()
            val functionFrequencyReader = emitterFileLocation.getReaderOfFile()
            var line = functionFrequencyReader.readLine()
            while (line != null) {
                emitTask(line)
                line = functionFrequencyReader.readLine()
            }
            functionFrequencyReader.close()
            functionExecutionReader.close()
        }
    }

    fun skipCsvHeader() {
        functionExecutionReader.readLine()
    }

    fun getFunctionTime(): Double {
        var line = functionExecutionReader.readLine()
        if (line == null) {
            resetFunctionExecutionReader()
            skipCsvHeader()
            line = functionExecutionReader.readLine()
        }
        return line.toDouble()
    }

    fun resetFunctionExecutionReader() {
        functionExecutionReader.close()
        functionExecutionReader = functionExecutionFileLocation.getReaderOfFile()
    }

    private fun emitTask(line: String) {
        val (emitAt, amountTasksToEmit) = parseLine(line)
        sleepTillNextEmit(emitAt)
        emitTask(amountTasksToEmit)
    }

    fun emitTask(amountTasksToEmit: Int) {
        for (i in 1..amountTasksToEmit) {
            val functionTime = getFunctionTime()
            val task = getNewTask(functionTime)
            strategy.addTaskToQueue(task)
        }
    }

    private fun parseLine(line: String): Pair<Long, Int> {
        val parts = line.split(",")
        val emitAt = parts[0].toDouble().toLong()
        val numberTasksToEmit = parts[1].toInt()
        return Pair(emitAt, numberTasksToEmit)
    }

    private fun sleepTillNextEmit(emitAt: Long) {
        val emitAtDelta = emitAt - lastEmitAt
        Thread.sleep(emitAtDelta * 1000)
        lastEmitAt = emitAt
    }

    abstract fun getNewTask(functionTime: Double): SupportedTask

    @PreDestroy
    fun shutdown() {
        isRunning = false
    }
}
