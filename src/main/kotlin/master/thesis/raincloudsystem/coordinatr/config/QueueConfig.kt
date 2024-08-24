package master.thesis.raincloudsystem.coordinatr.config

import master.thesis.raincloudsystem.shared.task.RCSTask
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.LinkedBlockingDeque

@Configuration
class QueueConfig {

    @Bean
    fun queue(): LinkedBlockingDeque<RCSTask> {
        return LinkedBlockingDeque<RCSTask>()
    }
}
