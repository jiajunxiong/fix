package com.cashalgo.fix.message

// from exchange message
data class ExecutionReport(
    val execId: String,
    val orderId: String,
    val symbol: String,
    val price: Double,
    val quantity: Double,
    val execQuantity: Double?,
    val execPrice: Double?,
    val cumExecQuantity: Double?,
    val cumExecPrice: Double?,
    val status: String,
    val timestamp: Long,
    val active: Boolean
)