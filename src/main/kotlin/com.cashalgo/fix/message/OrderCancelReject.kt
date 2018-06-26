package com.cashalgo.fix.message

// from exchange message
data class OrderCancelReject(
    val orderId: String,
    val clOrdId: String,
    val origClOrdId: String,
    val status: String,
    val cxlRejResponseTo: String
)