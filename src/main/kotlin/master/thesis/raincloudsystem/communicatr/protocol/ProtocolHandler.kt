package master.thesis.raincloudsystem.communicatr.protocol

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import master.thesis.raincloudsystem.communicatr.model.enums.MessageType
import org.springframework.stereotype.Component
import java.util.EnumMap

@Component
class ProtocolHandler(protocols: List<Protocol>) {

    private val handler: MutableMap<MessageType, Protocol> = EnumMap(MessageType::class.java)

    init {
        protocols.forEach {
            handler[it.type] = it
        }
    }

    fun read(request: String) {
        val tree = Json.decodeFromString<JsonObject>(request)
        val messageType = tree["messageType"]!!
        val messageTypeString = Json.decodeFromJsonElement<String>(messageType)
        val enumValue = enumValueOf<MessageType>(messageTypeString)
        handler[enumValue]!!.read(request)
    }
}
