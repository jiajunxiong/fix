package com.cashalgo.fix.message

import kotlinx.coroutines.experimental.channels.Channel

sealed class OMSMessage
// server message
data class ExecutionReportMessage(
    val exchange: String,
    val er: ExecutionReport,
    val data: Any?) : OMSMessage()

data class OrderCancelRejectMessage(
    val exchange: String,
    val cr: OrderCancelReject,
    val data: Any?) : OMSMessage()

data class OrderCancelReplaceRejectMessage(
    val exchange: String,
    val rr: OrderCancelReplaceReject,
    val data: Any?) : OMSMessage()

// client message
data class NewOrderMessage(
    val exchange: String,
    val correlId: String?,
    val symbol: String,
    val price: Double,
    val quantity: Double,
    val extraParams: Any?,
    val responseChannel: Channel<String>?): OMSMessage()

data class CancelOrderMessage(
    val id: String,
    val correlId: String?,
    val extraParams: Any?,
    val responseChannel: Channel<String>?): OMSMessage()

data class ReplaceOrderMessage(
    val id: String,
    val correlId: String?,
    val price: Double,
    val quantity: Double,
    val extraParams: Any?,
    val responseChannel: Channel<String>?): OMSMessage()