package com.example.springjpa.monitoring

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TrackOrderProcessing(
    val metricName: String = OrderMetrics.ORDER_PROCESSING_DURATION,
)
