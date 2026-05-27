package com.example.springjpa.monitoring

import io.micrometer.prometheusmetrics.PrometheusMeterRegistry
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class PrometheusController(
    private val prometheusMeterRegistry: PrometheusMeterRegistry,
) {
    @GetMapping("/actuator/prometheus", produces = ["text/plain; version=0.0.4; charset=utf-8"])
    fun scrape(): String = prometheusMeterRegistry.scrape()
}
