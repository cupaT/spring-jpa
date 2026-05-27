package com.example.springjpa.monitoring

import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import io.micrometer.core.instrument.Counter
import org.springframework.stereotype.Component

@Component
class OrderMetrics(private val meterRegistry: MeterRegistry) {
    val processingDuration: Timer = Timer.builder(ORDER_PROCESSING_DURATION)
        .description("Order creation request processing duration")
        .tag("outcome", "none")
        .tag("reason", "none")
        .publishPercentileHistogram()
        .register(meterRegistry)

    private val ordersCreated: Counter = Counter.builder(ORDERS_CREATED)
        .description("Successfully created orders count")
        .register(meterRegistry)

    init {
        meterRegistry.counter(BUSINESS_ERRORS, "type", "validation")
        meterRegistry.counter(BUSINESS_ERRORS, "type", "payment")
        meterRegistry.counter(ORDER_CREATION_FAILED, "reason", "stock_empty")
    }

    fun recordOrderCreated() {
        ordersCreated.increment()
    }

    fun recordBusinessError(type: String) {
        meterRegistry.counter(BUSINESS_ERRORS, "type", type).increment()
    }

    fun recordOrderCreationFailed(reason: String) {
        meterRegistry.counter(ORDER_CREATION_FAILED, "reason", reason).increment()
    }

    companion object {
        const val ORDER_PROCESSING_DURATION = "order_processing_duration_seconds"
        const val ORDERS_CREATED = "orders_created_total"
        const val BUSINESS_ERRORS = "business_errors_total"
        const val ORDER_CREATION_FAILED = "order_creation_failed_total"
    }
}
