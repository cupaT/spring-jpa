package com.example.springjpa.monitoring

import com.example.springjpa.application.exception.OrderCreationException
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Aspect
@Component
class OrderMetricsAspect(
    private val metrics: OrderMetrics,
    private val meterRegistry: MeterRegistry,
) {
    @Around("@annotation(track)")
    fun around(joinPoint: ProceedingJoinPoint, track: TrackOrderProcessing): Any? {
        val sample = Timer.start(meterRegistry)

        return try {
            val result = joinPoint.proceed()
            sample.stop(timer(track.metricName, "outcome", "success", "reason", "none"))
            metrics.recordOrderCreated()
            result
        } catch (ex: OrderCreationException) {
            sample.stop(timer(track.metricName, "outcome", "error", "reason", ex.reason))
            metrics.recordOrderCreationFailed(ex.reason)
            throw ex
        } catch (ex: IllegalArgumentException) {
            sample.stop(timer(track.metricName, "outcome", "error", "reason", "validation"))
            metrics.recordBusinessError("validation")
            throw ex
        }
    }

    private fun timer(metricName: String, vararg tags: String): Timer =
        Timer.builder(metricName)
            .tags(*tags)
            .publishPercentileHistogram()
            .register(meterRegistry)
}
