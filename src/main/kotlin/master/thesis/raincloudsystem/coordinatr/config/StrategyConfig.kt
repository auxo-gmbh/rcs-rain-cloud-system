package master.thesis.raincloudsystem.coordinatr.config

import master.thesis.raincloudsystem.shared.task.SupportedTask
import master.thesis.raincloudsystem.shared.utils.RemotePort
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ConcurrentHashMap

@Configuration
class StrategyConfig {

    @Bean
    fun offloadedTasks(): ConcurrentHashMap<String, SupportedTask> {
        return ConcurrentHashMap<String, SupportedTask>()
    }

    @Bean
    fun gossipsQuality(): ConcurrentHashMap<RemotePort, Int> {
        return ConcurrentHashMap()
    }
}
