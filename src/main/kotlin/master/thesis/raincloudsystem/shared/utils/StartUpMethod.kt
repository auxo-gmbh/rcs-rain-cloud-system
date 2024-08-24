package master.thesis.raincloudsystem.shared.utils

import org.springframework.boot.context.event.ApplicationStartedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Async

@Async
@EventListener(ApplicationStartedEvent::class)
annotation class StartUpMethod()
