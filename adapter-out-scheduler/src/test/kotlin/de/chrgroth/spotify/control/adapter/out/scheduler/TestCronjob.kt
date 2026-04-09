package de.chrgroth.spotify.control.adapter.out.scheduler

import io.quarkus.scheduler.Scheduled
import jakarta.enterprise.context.ApplicationScoped

@ApplicationScoped
internal class TestCronjob {
  @Scheduled(every = "1h")
  fun run() = Unit
}

@ApplicationScoped
internal class AnotherTestCronjob {
  @Scheduled(every = "2h")
  fun run() = Unit
}
