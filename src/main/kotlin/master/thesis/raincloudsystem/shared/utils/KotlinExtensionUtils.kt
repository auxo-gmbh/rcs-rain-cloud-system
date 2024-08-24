package master.thesis.raincloudsystem.shared.utils

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import master.thesis.raincloudsystem.communicatr.model.message.Message
import master.thesis.raincloudsystem.communicatr.model.message.RequestMessage
import java.io.BufferedReader
import java.io.FileReader
import java.net.Socket
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

inline fun <T : Any> T?.isNotNull(function: (it: T) -> Unit) {
    if (this != null) {
        function(this)
    }
}

inline fun <T : Any, R> T?.ifPresentOrElseThrow(presentFunction: (it: T) -> R, exception: Exception): R {
    if (this != null) {
        return presentFunction(this)
    }
    throw exception
}

inline fun Boolean.whileIsTrue(function: () -> Unit) {
    while (this) {
        function()
    }
}

fun Socket.closeIfNotAlreadyClosed() {
    if (!this.isClosed) {
        this.close()
    }
}

val json = Json { ignoreUnknownKeys = true }

fun Message.encodeToString(): String {
    return json.encodeToString(this)
}

inline fun <reified T : Message> String.decodeFromString(): T {
    return json.decodeFromString(this)
}

fun RequestMessage.isPassedDeadline() = this.deadline.isBefore(Instant.now())

fun Long.toWholeMinute() = 60000 * (this / 60000)

fun FileLocation.getReaderOfFile() = BufferedReader(FileReader(this))

typealias FileLocation = String
typealias TaskType = String
typealias RemotePort = Int
typealias Edges = ConcurrentHashMap<RemotePort, Double>
typealias PheromonesTable = ConcurrentHashMap<TaskType, Edges>
typealias QualityTable = ConcurrentHashMap<RemotePort, List<TaskType>>
